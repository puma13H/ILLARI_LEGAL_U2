import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Cliente, CreateUserResult } from '../models';

@Injectable({ providedIn: 'root' })
export class ClienteService {
  constructor(private http: HttpClient) {}

  list() {
    return this.http.get<Cliente[]>(`${environment.apiUrl}/clientes`);
  }

  create(payload: Partial<Cliente>) {
    // return full response so frontend can handle non-JSON responses gracefully
    return this.http.post(`${environment.apiUrl}/clientes`, payload, { observe: 'response' as const });
  }

  update(id: number, payload: Partial<Cliente>) {
    return this.http.put<Cliente>(`${environment.apiUrl}/clientes/${id}`, payload);
  }

  delete(id: number) {
    return this.http.delete<void>(`${environment.apiUrl}/clientes/${id}`);
  }
}
