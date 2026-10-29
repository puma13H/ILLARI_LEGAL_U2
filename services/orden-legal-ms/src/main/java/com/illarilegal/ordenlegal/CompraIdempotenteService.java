package com.illarilegal.ordenlegal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class CompraIdempotenteService {
    private final CompraIdempotenciaRepository claves;
    private final OrdenLegalController ordenes;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    public CompraIdempotenteService(CompraIdempotenciaRepository claves, OrdenLegalController ordenes,
                                    ObjectMapper mapper, PlatformTransactionManager manager) {
        this.claves=claves; this.ordenes=ordenes; this.mapper=mapper;
        this.tx=new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public OrdenLegalController.OrdenLegal crear(String clave, OrdenLegalController.NuevaOrden solicitud, Jwt jwt) {
        if(clave==null || !clave.matches("[A-Za-z0-9._:-]{8,128}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Se requiere Idempotency-Key (8 a 128 caracteres).");
        var normal=ReglasCompra.normalizar(solicitud,jwt);
        String id=hash(jwt.getSubject()+"\n"+clave);
        String huella=hash(json(normal));
        // Reservation commits before acquiring the execution lock. No outer transaction/connection is held.
        try {
            tx.executeWithoutResult(status-> {
                if(!claves.existsById(id)) claves.saveAndFlush(new CompraIdempotenciaEntity(id,jwt.getSubject(),clave,huella));
            });
        } catch(DataIntegrityViolationException race) {
            // Another instance reserved this PK; its transaction has committed before this unique-key failure.
            if(!claves.existsById(id)) throw race;
        }
        return tx.execute(status->{
            var row=claves.bloquear(id).orElseThrow(()->new IllegalStateException("Reserva de compra no disponible."));
            if(!huella.equals(row.getHuella()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency-Key ya utilizada con otros datos.");
            if(row.getRespuesta()!=null) {
                try { return mapper.readValue(row.getRespuesta(),OrdenLegalController.OrdenLegal.class); }
                catch(Exception e) { throw new IllegalStateException("Respuesta de compra almacenada inválida.",e); }
            }
            var orden=ordenes.crear(normal,jwt);
            row.completar(orden.id(),json(orden));
            claves.save(row);
            return orden;
        });
    }
    private String json(Object o) {
        try{return mapper.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException("No se pudo serializar la compra.",e);}
    }
    private static String hash(String s) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}

