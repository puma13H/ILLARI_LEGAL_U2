import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CarritoPublicoService } from '../../core/services/carrito-publico.service';
import { AuthService } from '../../core/auth/auth-service';

@Component({
  selector: 'app-layout',
  standalone: true,
  // Angular 22 defaults to OnPush; legacy routed screens still use observable assignments.
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.css',
})
export class LayoutComponent {
  readonly cart=inject(CarritoPublicoService);
  private readonly router=inject(Router);
  search='';
  buscar():void{void this.router.navigate(['/tienda-juridica'],{queryParams:{buscar:this.search.trim()}});}
  menuAbierto = false;
  async registrarse(): Promise<void> {
    try { await this.auth.iniciarLogin('/dashboard', true); }
    catch { window.alert('No se pudo abrir el registro. Inténtalo nuevamente.'); }
  }
  protected readonly auth = inject(AuthService);
}
