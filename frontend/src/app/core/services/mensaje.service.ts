import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Mensaje } from '../models';

@Injectable({ providedIn: 'root' })
export class MensajeService {
  constructor(private http: HttpClient) {}

  list(casoId: number) {
    return this.http.get<Mensaje[]>(`${environment.apiUrl}/casos/${casoId}/mensajes`);
  }

  send(casoId: number, contenido: string) {
    return this.http.post<Mensaje>(`${environment.apiUrl}/casos/${casoId}/mensajes`, { contenido });
  }
}
