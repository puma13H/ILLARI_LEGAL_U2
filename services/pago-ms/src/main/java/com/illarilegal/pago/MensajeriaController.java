package com.illarilegal.pago;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
@RestController @RequestMapping("/api/v1/pagos/mensajeria")
public class MensajeriaController {
    private final MensajeriaRecoveryService recovery;
    private final PagoOutboxRepository outbox;
    public MensajeriaController(MensajeriaRecoveryService recovery,PagoOutboxRepository outbox){this.recovery=recovery;this.outbox=outbox;}
    public record Replay(String payloadCorregido,String motivo) {}
    @GetMapping("/fallidos") public java.util.List<MensajeriaRecoveryService.Vista> fallidos(
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size){return recovery.listar(page,size);}
    @GetMapping("/fallidos/{id}") public MensajeriaRecoveryService.Detalle detalle(@PathVariable String id){return recovery.detalle(id);}
    @GetMapping("/operaciones") public java.util.List<ReplayAuditoriaEntity.Vista> operaciones(){return recovery.operaciones();}
    @PostMapping("/fallidos/{id}/reprocesar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensajeriaRecoveryService.Vista replay(@PathVariable String id,@RequestBody Replay body,@AuthenticationPrincipal Jwt jwt){
        return recovery.solicitarReplay(id,body.payloadCorregido(),body.motivo(),jwt.getSubject());
    }
    @PostMapping("/outbox/{id}/reintentar") @Transactional @ResponseStatus(HttpStatus.ACCEPTED)
    public void retry(@PathVariable Long id,@RequestBody Replay body,@AuthenticationPrincipal Jwt jwt){
        if(body.motivo()==null || body.motivo().isBlank() || body.motivo().length()>500)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Indica el motivo.");
        var e=outbox.bloquear(id).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
        e.reintentar(jwt.getSubject(),body.motivo());outbox.save(e);
        recovery.auditar("OUTBOX_RETRY",id.toString(),jwt.getSubject(),body.motivo(),e.getPayload());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) public java.util.Map<String,String> invalid(IllegalArgumentException e){return java.util.Map.of("error","Solicitud de recuperación inválida.");}
}

