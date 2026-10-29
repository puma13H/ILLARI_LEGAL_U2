package com.illarilegal.ordenlegal;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
public final class ReglasCompra {
    private ReglasCompra() {}
    public static String metodo(String valor) {
        String m=valor==null?"":valor.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("YAPE","PLIN","BIPAY","TARJETA","TARJETA_RECHAZADA").contains(m))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Método de pago no admitido.");
        return m;
    }
    public static OrdenLegalController.NuevaOrden normalizar(OrdenLegalController.NuevaOrden r, Jwt jwt) {
        if(jwt==null || jwt.getSubject()==null || jwt.getSubject().isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if(r==null || r.detalles()==null || r.detalles().isEmpty() || r.detalles().size()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La compra requiere entre 1 y 100 detalles.");
        String cliente=r.idCliente()==null?jwt.getSubject():r.idCliente();
        if(cliente.isBlank() || cliente.length()>255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cliente inválido.");
        Object realm=jwt.getClaim("realm_access");
        boolean admin=realm instanceof Map<?,?> map && map.get("roles") instanceof Collection<?> roles
                && roles.stream().anyMatch(role->"ADMIN".equalsIgnoreCase(role.toString()));
        if(!cliente.equals(jwt.getSubject()) && !admin)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"No puedes comprar a nombre de otro cliente.");
        TreeMap<Long,Integer> cantidades=new TreeMap<>();
        for(var d:r.detalles()) {
            if(d==null || d.idServicio()==null || d.idServicio()<1 || d.cantidad()==null || d.cantidad()<1 || d.cantidad()>1000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Servicio y cantidad inválidos (1 a 1000).");
            int total=cantidades.getOrDefault(d.idServicio(),0)+d.cantidad();
            if(total>1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cantidad máxima por servicio: 1000.");
            cantidades.put(d.idServicio(),total);
        }
        return new OrdenLegalController.NuevaOrden(metodo(r.metodoPago()), cantidades.entrySet().stream()
                .map(e->new OrdenLegalController.DetalleNuevaOrden(e.getKey(),e.getValue())).toList(),cliente);
    }
}

