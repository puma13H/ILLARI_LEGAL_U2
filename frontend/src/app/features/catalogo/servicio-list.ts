import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ServicioLegalService } from './servicio.service';
import { ServicioLegal } from './servicio.model';
import { AuthService } from '../../core/auth/auth-service';

@Component({
  selector: 'app-servicio-list',
  standalone: true,
  imports: [RouterLink, CurrencyPipe, FormsModule],
  templateUrl: './servicio-list.html',
})
export class ServicioList implements OnInit {
  private readonly servicioApi = inject(ServicioLegalService);
  protected readonly auth = inject(AuthService);
  protected readonly servicios = signal<ServicioLegal[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly errorAccion = signal<string | null>(null);

  readonly busqueda = signal('');
  readonly filtrados = computed(() => this.servicios().filter(s => `${s.nombre} ${s.descripcion} ${s.categoria}`.toLowerCase().includes(this.busqueda().trim().toLowerCase())));
  private readonly route = inject(ActivatedRoute);
  ngOnInit(): void {
    this.route.queryParamMap.subscribe(p => this.busqueda.set(p.get('buscar') ?? ''));
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    this.servicioApi.listar().subscribe({
      next: (data) => {
        this.servicios.set(data);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar el catálogo de servicios. Verifica el Gateway en :18080.');
        this.cargando.set(false);
      },
    });
  }

  eliminar(id: number): void {
    if (!confirm(`¿Seguro que quieres eliminar el servicio ${id}?`)) {
      return;
    }
    this.errorAccion.set(null);
    this.servicioApi.eliminar(id).subscribe({
      next: () => this.cargar(),
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.errorAccion.set('No tienes permiso para esta acción. Inicia sesión como ADMIN.');
        } else {
          this.errorAccion.set('No se pudo eliminar el servicio.');
        }
      },
    });
  }
}
