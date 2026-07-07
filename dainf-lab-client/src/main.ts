import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app.component';
import { appConfig } from './app.config';

console.log('=== NOVO DEPLOY DO ALOVISIS APLICADO ===');

bootstrapApplication(AppComponent, appConfig).catch((err) =>
  console.error(err),
);
