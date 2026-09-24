package br.com.nebulatv.model;

public record PlayerSource(String name, String url, boolean external) {
    @Override public String toString(){ return name; }
}
