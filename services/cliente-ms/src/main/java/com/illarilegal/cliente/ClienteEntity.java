package com.illarilegal.cliente;

import jakarta.persistence.*;

@Entity
@Table(name = "clientes_distribuidos")
public class ClienteEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String dni;
    private String nombres;
    private String apellidos;
    private String email;
    private String telefono;
    private String direccion;
    private String estado;
    @Column(name = "keycloak_sub", unique = true) private String keycloakSub;
    protected ClienteEntity() {}
    ClienteEntity(String dni, String nombres, String apellidos, String email, String telefono,
            String direccion, String estado, String keycloakSub) {
        this.dni=dni; this.nombres=nombres; this.apellidos=apellidos; this.email=email;
        this.telefono=telefono; this.direccion=direccion; this.estado=estado; this.keycloakSub=keycloakSub;
    }
    public Long getId(){return id;}
    public String getDni(){return dni;}
    public String getNombres(){return nombres;}
    public String getApellidos(){return apellidos;}
    public String getEmail(){return email;}
    public String getTelefono(){return telefono;}
    public String getDireccion(){return direccion;}
    public String getEstado(){return estado;}
    public String getKeycloakSub(){return keycloakSub;}
    void modificar(String dni,String nombres,String apellidos,String email,String telefono,String direccion,String estado){
        this.dni=dni;this.nombres=nombres;this.apellidos=apellidos;this.email=email;
        this.telefono=telefono;this.direccion=direccion;this.estado=estado;
    }
}
