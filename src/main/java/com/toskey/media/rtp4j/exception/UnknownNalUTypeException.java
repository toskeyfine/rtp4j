package com.toskey.media.rtp4j.exception;

/**
 * UnknownNalUTypeException
 *
 * @author toskey
 * @version 1.0.0
 */
public class UnknownNalUTypeException extends Exception {

    private int nalUType;

    public UnknownNalUTypeException(int nalUType) {
        super();
        this.nalUType = nalUType;
    }

    public UnknownNalUTypeException(int nalUType, Throwable t) {
        super(t);
        this.nalUType = nalUType;
    }

    public int getNalUType() {
        return nalUType;
    }
}
