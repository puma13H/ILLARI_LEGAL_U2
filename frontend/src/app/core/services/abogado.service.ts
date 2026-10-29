import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Abogado, CreateUserResult } from '../models';

@Injectable({ providedIn: 'root' })
export class AbogadoService {
  constructor(private http: HttpClient) {}

  list() {
    return this.http.get<Abogado[]>(`${environment.apiUrl}/abogados`);
  }

  create(payload: Partial<Abogado>) {
    return this.http.post(`${environment.apiUrl}/abogados`, payload, { observe: 'response' as const });
  }

  update(id: number, payload: Partial<Abogado>) {
    return this.http.put<Abogado>(`${environment.apiUrl}/abogados/${id}`, payload);
  }

  delete(id: number) {
    return this.http.delete<void>(`${environment.apiUrl}/abogados/${id}`);
  }
}
