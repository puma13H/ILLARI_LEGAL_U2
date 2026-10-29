import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ServicioLegal } from './servicio.model';

@Injectable({ providedIn: 'root' })
export class ServicioLegalService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/servicios`;

  listar(): Observable<ServicioLegal[]> {
    return this.http.get<ServicioLegal[]>(this.base);
  }

  obtener(id: number): Observable<ServicioLegal> {
    return this.http.get<ServicioLegal>(`${this.base}/${id}`);
  }

  crear(servicio: ServicioLegal): Observable<ServicioLegal> {
    return this.http.post<ServicioLegal>(this.base, servicio);
  }

  actualizar(id: number, servicio: ServicioLegal): Observable<ServicioLegal> {
    return this.http.put<ServicioLegal>(`${this.base}/${id}`, servicio);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}

