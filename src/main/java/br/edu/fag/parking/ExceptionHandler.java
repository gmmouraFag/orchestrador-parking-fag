package br.edu.fag.parking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;

@RestControllerAdvice
public class ExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);
    private final LogService logs;
    public ExceptionHandler(LogService logs) { this.logs = logs; }
    @org.springframework.web.bind.annotation.ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Api.Error> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new Api.Error("INVALID_REQUEST", "Verifique os campos, a origem e as datas da requisição", Instant.now()));
    }
    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<Api.Error> unexpected(Exception exception) {
        logger.error("request_failed type={}", exception.getClass().getSimpleName());
        try { logs.internal("ERROR", "Falha interna no processamento de requisição"); }
        catch (Exception ignored) { logger.error("operational_log_persistence_failed"); }
        return ResponseEntity.internalServerError().body(new Api.Error("INTERNAL_ERROR", "Não foi possível processar a requisição", Instant.now()));
    }
}
