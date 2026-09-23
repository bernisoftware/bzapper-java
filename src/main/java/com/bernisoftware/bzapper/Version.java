package com.bernisoftware.bzapper;

/**
 * Versão do SDK. Bumpada pelo {@code scripts/release-sdks.sh} junto com o
 * {@code pom.xml} (o {@code VersionTest} trava a igualdade entre os dois).
 *
 * <p>Não é cosmética: vai no header {@code X-Bzapper-Client} de toda
 * requisição, e é por ele que a API sabe a quem avisar quando uma correção
 * exige atualizar o código da integração.
 */
public final class Version {
    private Version() {}

    /** Versão do artefato. */
    public static final String VERSION = "0.8.1";

    /** Identificação enviada em X-Bzapper-Client (e User-Agent). */
    public static final String CLIENT_ID = "bzapper-java/" + VERSION;
}
