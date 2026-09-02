package com.team5.domain.exception;

public class InstrumentNotFoundException extends TradeDomainException {

    private final Long instrumentId;

    public InstrumentNotFoundException(Long instrumentId) {
        super("Instrument not found with id: " + instrumentId);
        this.instrumentId = instrumentId;
    }

    public Long getInstrumentId() { return instrumentId; }
}