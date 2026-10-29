import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Documento } from '../models';

@Injectable({ providedIn: 'root' })
export class DocumentoService {
  constructor(private http: HttpClient) {}

  list(casoId: number) {
    return this.http.get<Documento[]>(`${environment.apiUrl}/documentos/caso/${casoId}`);
  }

  upload(casoId: number, nombre: string, archivo: File) {
    const form = new FormData();
    form.append('nombre', nombre);
    form.append('archivo', archivo);
    return this.http.post<Documento>(`${environment.apiUrl}/documentos/caso/${casoId}`, form);
  }

  download(id: number) {
    return this.http.get(`${environment.apiUrl}/documentos/${id}/download`, {
      responseType: 'blob',
      observe: 'response'
    });
  }
}
