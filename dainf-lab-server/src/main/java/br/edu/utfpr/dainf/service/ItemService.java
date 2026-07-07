package br.edu.utfpr.dainf.service;

import br.edu.utfpr.dainf.exception.ItemDeletionNotAllowedException;
import br.edu.utfpr.dainf.model.Item;
import br.edu.utfpr.dainf.repository.ItemRepository;
import br.edu.utfpr.dainf.repository.LeakageRepository;
import br.edu.utfpr.dainf.repository.LoanRepository;
import br.edu.utfpr.dainf.shared.CrudService;
import br.edu.utfpr.dainf.storage.StorageService;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ItemService extends CrudService<Long, Item, ItemRepository> {

    private final StorageService storageService;
    private final br.edu.utfpr.dainf.repository.InventoryRepository inventoryRepository;
    private final br.edu.utfpr.dainf.repository.InventoryTransactionRepository inventoryTransactionRepository;
    private final LeakageRepository leakageRepository;
    private final LoanRepository loanRepository;
    private final jakarta.persistence.EntityManager entityManager;

    public ItemService(StorageService storageService, br.edu.utfpr.dainf.repository.InventoryRepository inventoryRepository, br.edu.utfpr.dainf.repository.InventoryTransactionRepository inventoryTransactionRepository, LeakageRepository leakageRepository, LoanRepository loanRepository, jakarta.persistence.EntityManager entityManager) {
        this.storageService = storageService;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.leakageRepository = leakageRepository;
        this.loanRepository = loanRepository;
        this.entityManager = entityManager;
    }

    @Override
    public JpaSpecificationExecutor<Item> getSpecExecutor() {
        return repository;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteById(Long id) {
        Item item = repository.findById(id).orElse(null);
        if (item != null) {
            // Validar se o item pode ser deletado
            validateItemDeletion(item);
            
            entityManager.createQuery("DELETE FROM CartItem ci WHERE ci.item = :item")
                         .setParameter("item", item)
                         .executeUpdate();
                         
            inventoryRepository.findByItem(item).ifPresent(inventory -> {
                inventoryTransactionRepository.deleteByInventory(inventory);
                inventoryRepository.delete(inventory);
            });
            super.deleteById(id);
        }
    }

    /**
     * Valida se um item pode ser deletado verificando:
     * 1. Se possui empréstimos ativos (ONGOING ou OVERDUE)
     * 2. Se possui registros de perda/extravio
     * 3. Se possui histórico de transações (entradas/saídas)
     * 
     * @param item o item a ser validado
     * @throws ItemDeletionNotAllowedException se o item possui qualquer um dos históricos acima
     */
    private void validateItemDeletion(Item item) {
        StringBuilder errorMessage = new StringBuilder();
        
        // Verificar empréstimos ativos
        if (hasActiveLoan(item)) {
            errorMessage.append("Este item possui empréstimos ativo(s) e não pode ser excluído do cadastro. ");
            errorMessage.append("Favor finalizar os empréstimos antes de excluir o item.");
        }
        
        // Verificar registros de perda/extravio
        if (hasLeakageHistory(item)) {
            if (errorMessage.length() > 0) errorMessage.append(" ");
            errorMessage.append("Este item possui registro(s) de perda/extravio e não pode ser excluído do cadastro.");
        }
        
        // Verificar histórico de transações (entradas/saídas)
        if (hasTransactionHistory(item)) {
            if (errorMessage.length() > 0) errorMessage.append(" ");
            errorMessage.append("Este item possui histórico de transações (entrada/saída) e não pode ser excluído do cadastro. ");
            errorMessage.append("Use a função de Registrar Perda/Extravio para remover quantidades.");
        }
        
        if (errorMessage.length() > 0) {
            throw new ItemDeletionNotAllowedException(errorMessage.toString());
        }
    }

    /**
     * Verifica se o item possui empréstimos ativos (ONGOING ou OVERDUE)
     * 
     * @param item o item a verificar
     * @return true se possui empréstimos ativos, false caso contrário
     */
    private boolean hasActiveLoan(Item item) {
        return !loanRepository.findActiveByItem(item.getId()).isEmpty();
    }

    /**
     * Verifica se o item possui registros de perda/extravio
     * 
     * @param item o item a verificar
     * @return true se possui registros de perda/extravio, false caso contrário
     */
    private boolean hasLeakageHistory(Item item) {
        Long leakageCount = (Long) entityManager.createQuery(
                "SELECT COUNT(li) FROM LeakageItem li WHERE li.item.id = :itemId"
        ).setParameter("itemId", item.getId()).getSingleResult();
        return leakageCount > 0;
    }

    /**
     * Verifica se o item possui histórico de transações no inventário
     * 
     * @param item o item a verificar
     * @return true se possui histórico de transações, false caso contrário
     */
    private boolean hasTransactionHistory(Item item) {
        return inventoryRepository.findByItem(item)
                .map(inventory -> {
                    Long transactionCount = (Long) entityManager.createQuery(
                            "SELECT COUNT(it) FROM InventoryTransaction it WHERE it.inventory.id = :inventoryId"
                    ).setParameter("inventoryId", inventory.getId()).getSingleResult();
                    return transactionCount > 0;
                })
                .orElse(false);
    }

    @Override
    public Item save(Item entity) {
        Optional.ofNullable(entity.getAssets()).ifPresent(assets ->
                assets.forEach(asset -> asset.setItem(entity))
        );

        Optional.ofNullable(entity.getImages()).ifPresent(images ->
                images.forEach(image -> image.setItem(entity))
        );

        Item savedEntity = super.save(entity);

        if (savedEntity.getCode() == null || savedEntity.getCode().trim().isEmpty()) {
            savedEntity.setCode(String.valueOf(savedEntity.getId()));
            savedEntity = super.save(savedEntity);
        }

        if (savedEntity.getImages() != null && moveTempImages(savedEntity)) {
            savedEntity = super.save(savedEntity);
        }

        if (savedEntity.getMinimumStock() == null) {
            savedEntity.setMinimumStock(java.math.BigDecimal.ZERO);
            savedEntity = super.save(savedEntity);
        }

        return savedEntity;
    }

    private boolean moveTempImages(Item entity) {
        AtomicBoolean changed = new AtomicBoolean(false);

        entity.getImages().forEach(image -> {
            if (image.getName().contains("temp/")) {
                String newName = image.getName().replace("temp/", entity.getId() + "/");
                storageService.moveToPermanentFolder("item", image.getName(), newName);
                image.setName(newName);
                changed.set(true);
            }
        });

        return changed.get();
    }
}
