import { panelGuard } from './features/panel/panel.guard';
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { dashboardRedirectGuard } from './core/guards/dashboard.guard';
import { LayoutComponent } from './shared/layout/layout.component';

export const routes: Routes = [
  {
    path: 'auth/callback',
    loadComponent: () => import('./core/auth/auth-callback').then(m => m.AuthCallback)
  },
  {
    path: 'login/:role',
    redirectTo: 'login',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'tienda-juridica',
    loadComponent: () => import('./features/tienda-juridica/tienda-juridica.component').then(m => m.TiendaJuridicaComponent)
  },
  {
    path: '',
    component: LayoutComponent,
    children: [
      {
        path: '',
        loadComponent: () => import('./core/inicio/inicio').then(m => m.Inicio)
      },
      {
        path: 'servicios',
        loadComponent: () => import('./features/catalogo/servicio-list').then(m => m.ServicioList)
      },
      {
        path: 'servicios/nuevo',
        canActivate: [authGuard, roleGuard('ADMIN')],
        loadComponent: () => import('./features/catalogo/servicio-form').then(m => m.ServicioForm)
      },
      {
        path: 'servicios/:id/editar',
        canActivate: [authGuard, roleGuard('ADMIN')],
        loadComponent: () => import('./features/catalogo/servicio-form').then(m => m.ServicioForm)
      },
      { path: 'mis-ordenes', canActivate: [authGuard], loadComponent: () => import('./features/ordenes/mis-ordenes.component').then(m => m.MisOrdenesComponent) },
      { path: 'dashboard', canActivate: [authGuard, dashboardRedirectGuard], loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'dashboard/admin', canActivate: [authGuard, roleGuard('ADMIN')], loadComponent: () => import('./features/panel/panel.component').then(m => m.PanelComponent) },
      { path: 'dashboard/abogado', canActivate: [authGuard, roleGuard('ABOGADO')], loadComponent: () => import('./features/panel/panel.component').then(m => m.PanelComponent) },
      { path: 'dashboard/cliente', canActivate: [authGuard, roleGuard('CLIENTE')], loadComponent: () => import('./features/panel/panel.component').then(m => m.PanelComponent) },
      { path: 'panel/:modulo', canActivate: [authGuard, panelGuard], loadComponent: () => import('./features/panel/panel.component').then(m => m.PanelComponent) },
      { path: 'resumen-real', canActivate: [authGuard], loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'usuarios', canActivate: [authGuard, roleGuard('admin')], loadComponent: () => import('./features/usuarios/usuarios.component').then(m => m.UsuariosComponent) },
      { path: 'casos', canActivate: [authGuard], loadComponent: () => import('./features/casos/casos-list.component').then(m => m.CasosListComponent) },
      { path: 'casos/nuevo', loadComponent: () => import('./features/casos/caso-create.component').then(m => m.CasoCreateComponent), canActivate: [authGuard, roleGuard('admin','abogado')] },
      { path: 'casos/:id', canActivate: [authGuard], loadComponent: () => import('./features/casos/caso-detail.component').then(m => m.CasoDetailComponent) },
      { path: 'clientes', canActivate: [authGuard, roleGuard('admin','abogado')], loadComponent: () => import('./features/clientes/clientes.component').then(m => m.ClientesComponent) },
      { path: 'abogados', canActivate: [authGuard, roleGuard('admin')], loadComponent: () => import('./features/abogados/abogados.component').then(m => m.AbogadosComponent) },
      { path: 'audiencias', canActivate: [authGuard], loadComponent: () => import('./features/audiencias/audiencias.component').then(m => m.AudienciasComponent) },
      { path: 'documentos', canActivate: [authGuard], loadComponent: () => import('./features/documentos/documentos.component').then(m => m.DocumentosComponent) },
      { path: 'tareas', canActivate: [authGuard], loadComponent: () => import('./features/tareas/tareas.component').then(m => m.TareasComponent) },
      { path: 'calendario', canActivate: [authGuard], loadComponent: () => import('./features/calendario/calendario.component').then(m => m.CalendarioComponent) },
      { path: 'reportes', canActivate: [authGuard], loadComponent: () => import('./features/reportes/reportes.component').then(m => m.ReportesComponent) },
      { path: 'mensajes', canActivate: [authGuard], loadComponent: () => import('./features/mensajes/mensajes.component').then(m => m.MensajesComponent) },
      { path: 'configuracion', canActivate: [authGuard], loadComponent: () => import('./features/configuracion/configuracion.component').then(m => m.ConfiguracionComponent) }
    ]
  },
  { path: '**', redirectTo: '' }
];
