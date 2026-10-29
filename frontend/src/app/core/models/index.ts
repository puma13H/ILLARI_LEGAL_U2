export interface AuthUser {
  userId: number;
  name: string;
  email: string;
  role: 'admin' | 'abogado' | 'cliente';
  token: string;
  avatarUrl?: string;
}

export interface Caso {
  id: number;
  nombreCaso: string;
  clienteId: number;
  clienteNombre: string;
  abogadoId: number;
  abogadoNombre: string;
  estado: string;
  fechaApertura: string;
  descripcion?: string;
  tarifa?: number;
  fechaAudiencia?: string;
}

export interface Cliente {
  id: number;
  nombre: string;
  telefono: string;
  email: string;
  direccion?: string;
  userId?: number;
}

export interface Abogado {
  id: number;
  nombre: string;
  telefono: string;
  email: string;
  especialidad: string;
  direccion?: string;
  userId?: number;
}

export interface Mensaje {
  id: number;
  casoId: number;
  userId: number;
  userName: string;
  contenido: string;
}

export interface Documento {
  id: number;
  nombre: string;
  tipoArchivo: string;
  tamanoArchivo: number;
  casoId: number;
}

export interface DashboardStats {
  totalCasos: number;
  totalClientes: number;
  totalAbogados: number;
  casosActivos: number;
}

export interface CreateUserResult {
  email: string;
  passwordTemporal: string;
}
