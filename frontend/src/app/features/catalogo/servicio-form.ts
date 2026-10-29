import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ServicioLegalService } from './servicio.service';

@Component({
  selector: 'app-servicio-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './servicio-form.html',
})
export class ServicioForm {
  private readonly fb = inject(FormBuilder);
  private readonly servicioApi = inject(ServicioLegalService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly id = signal<number | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    descripcion: ['', [Validators.maxLength(255)]],
    categoria: ['CONSULTA', [Validators.required, Validators.maxLength(80)]],
    precio: [0, [Validators.required, Validators.min(0)]],
    estado: ['ACTIVO'],
  });

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.id.set(id);
      this.cargando.set(true);
      this.servicioApi.obtener(id).subscribe({
        next: (servicio) => {
          this.form.patchValue({
            nombre: servicio.nombre,
            descripcion: servicio.descripcion ?? '',
            categoria: servicio.categoria ?? 'CONSULTA',
            precio: Number(servicio.precio ?? 0),
            estado: servicio.estado ?? 'ACTIVO',
          });
          this.cargando.set(false);
        },
        error: () => {
          this.error.set('No se pudo cargar el servicio.');
          this.cargando.set(false);
        },
      });
    }
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const valor = this.form.getRawValue();
    const id = this.id();
    const peticion = id ? this.servicioApi.actualizar(id, valor) : this.servicioApi.crear(valor);

    peticion.subscribe({
      next: () => this.router.navigate(['/servicios']),
      error: (err: HttpErrorResponse) => {
        this.error.set(
          err.status === 403
            ? 'No tienes permiso para esta acción.'
            : 'No se pudo guardar el servicio.',
        );
      },
    });
  }

  cancelar(): void {
    this.router.navigate(['/servicios']);
  }

  protected mensajeValidacion(campo: 'nombre' | 'descripcion' | 'categoria' | 'precio'): string {
    const control = this.form.controls[campo];
    if (!control.touched) return '';
    if (control.hasError('required')) return 'Este campo es obligatorio.';
    if (control.hasError('maxlength')) {
      return `Máximo ${control.getError('maxlength').requiredLength} caracteres.`;
    }
    if (control.hasError('min')) return 'El precio no puede ser negativo.';
    return '';
  }
}
