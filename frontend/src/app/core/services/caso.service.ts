import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Caso } from '../models';

@Injectable({ providedIn: 'root' })
export class CasoService {
  constructor(private http: HttpClient) {}

  list() {
    return this.http.get<Caso[]>(`${environment.apiUrl}/casos`);
  }

  get(id: number) {
    return this.http.get<Caso>(`${environment.apiUrl}/casos/${id}`);
  }

  create(payload: Partial<Caso> & { clienteId: number; abogadoId?: number }) {
    return this.http.post<Caso>(`${environment.apiUrl}/casos`, payload);
  }

  update(id: number, payload: Partial<Caso> & { clienteId: number }) {
    return this.http.put<Caso>(`${environment.apiUrl}/casos/${id}`, payload);
  }

  delete(id: number) {
    return this.http.delete<void>(`${environment.apiUrl}/casos/${id}`);
  }
}
