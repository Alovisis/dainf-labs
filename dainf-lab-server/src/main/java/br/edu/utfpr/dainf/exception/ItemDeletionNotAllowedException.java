package br.edu.utfpr.dainf.exception;

public class ItemDeletionNotAllowedException extends RuntimeException {
    public ItemDeletionNotAllowedException(String message) {
        super(message);
    }
}
