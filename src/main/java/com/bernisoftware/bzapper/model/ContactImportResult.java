package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Per-row outcome of {@code importContacts}. {@code skippedRows} and {@code errors} are never
 * null — an import with nothing to report carries empty lists.
 *
 * @param dryRun      true when nothing was written ({@code dry_run})
 * @param total       rows received
 * @param created     contacts created
 * @param updated     existing contacts whose informed fields were updated
 * @param skipped     rows deliberately not written (suppressed/opted-out/blocked, duplicates…)
 * @param failed      rows rejected by validation or by a write error
 * @param skippedRows why each skipped row was skipped
 * @param errors      why each failed row failed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContactImportResult(
        @JsonProperty("dry_run") boolean dryRun,
        @JsonProperty("total") int total,
        @JsonProperty("created") int created,
        @JsonProperty("updated") int updated,
        @JsonProperty("skipped") int skipped,
        @JsonProperty("failed") int failed,
        @JsonProperty("skipped_rows") List<ContactImportIssue> skippedRows,
        @JsonProperty("errors") List<ContactImportIssue> errors) {

    public ContactImportResult {
        skippedRows = skippedRows == null ? List.of() : List.copyOf(skippedRows);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
