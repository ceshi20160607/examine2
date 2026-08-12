package com.unique.examine.openapi.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiCanonicalRequestTest {
    @Test
    void canonicalizesEverySignedFieldAndSortsEncodedQuery() {
        var canonical = OpenApiCanonicalRequest.canonical(
                "get",
                "/openapi/./v1/ping",
                "z=last&a=hello%20world&a=first&empty",
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8),
                "1785196800",
                "nonce-1234567890",
                "operation-1"
        );

        assertThat(canonical).isEqualTo("""
                GET
                /openapi/v1/ping
                a=first&a=hello%20world&empty=&z=last
                4062edaf750fb8074e7e83e0c9028c94e32468a8b6f1614774328ef045150f93
                1785196800
                nonce-1234567890
                operation-1""");
    }

    @Test
    void producesLowercaseHmacAndRejectsUppercaseCandidate() {
        var canonical = "GET\n/openapi/v1/ping\n\nbody\ntime\nnonce\nkey";
        var secret = "only-in-secret-manager".getBytes(StandardCharsets.UTF_8);
        var signature = OpenApiCanonicalRequest.signature(secret, canonical);

        assertThat(signature).matches("[0-9a-f]{64}");
        assertThat(OpenApiCanonicalRequest.verify(secret, canonical, signature)).isTrue();
        assertThat(OpenApiCanonicalRequest.verify(
                secret, canonical, signature.toUpperCase())).isFalse();
    }

    @Test
    void changesSignatureWhenIdempotencyKeyChanges() {
        var first = OpenApiCanonicalRequest.canonical(
                "GET", "/openapi/v1/ping", null, new byte[0],
                "1785196800", "nonce-1234567890", "operation-1");
        var second = OpenApiCanonicalRequest.canonical(
                "GET", "/openapi/v1/ping", null, new byte[0],
                "1785196800", "nonce-1234567890", "operation-2");

        assertThat(OpenApiCanonicalRequest.signature(new byte[]{1, 2, 3}, first))
                .isNotEqualTo(OpenApiCanonicalRequest.signature(new byte[]{1, 2, 3}, second));
    }

    @Test
    void freezesRecordCreatePathBodyHashAndStableIdempotencyIdentity() {
        var body = """
                {"state":"ACTIVE","values":{"customer_name":"Sensitive Acme"}}"""
                .getBytes(StandardCharsets.UTF_8);

        var canonical = OpenApiCanonicalRequest.canonical(
                "post", "/openapi/v1/modules/Purchase_order/records", null,
                body, "1785196800", "nonce-record-create-001",
                "create-purchase-order-001");

        assertThat(canonical).isEqualTo("""
                POST
                /openapi/v1/modules/Purchase_order/records

                484a67cea0997c6eeb5f3b1acd62fd31e5ceafbc67d703d6df5490eece417f2c
                1785196800
                nonce-record-create-001
                create-purchase-order-001""");
        assertThat(canonical).doesNotContain("Sensitive Acme", "customer_name");
    }

    @Test
    void freezesRecordListAndDetailCanonicalPathAndSortedQuery() {
        var list = OpenApiCanonicalRequest.canonical(
                "GET", "/openapi/v1/modules/Purchase_order/records",
                "sort=updated_at%2cdesc&size=20&page=2", new byte[0],
                "1785196800", "nonce-record-list-0001", "record-list-1");
        var detail = OpenApiCanonicalRequest.canonical(
                "GET", "/openapi/v1/modules/Purchase_order/records/42",
                null, new byte[0], "1785196800",
                "nonce-record-detail-01", "record-detail-1");

        assertThat(list).contains("""
                /openapi/v1/modules/Purchase_order/records
                page=2&size=20&sort=updated_at%2Cdesc
                """);
        assertThat(detail).contains("""
                /openapi/v1/modules/Purchase_order/records/42

                """);
        assertThat(OpenApiCanonicalRequest.signature(new byte[]{1, 2, 3}, list))
                .isNotEqualTo(OpenApiCanonicalRequest.signature(
                        new byte[]{1, 2, 3}, detail));
    }

    @Test
    void signsRecordUpdateBodyAndExactLifecycleActionPath() {
        var updateBody = """
                {"expectedVersion":3,"values":{"customer_name":"Sensitive Acme"}}"""
                .getBytes(StandardCharsets.UTF_8);
        var changedUpdateBody = """
                {"expectedVersion":4,"values":{"customer_name":"Sensitive Acme"}}"""
                .getBytes(StandardCharsets.UTF_8);
        var lifecycleBody = "{\"expectedVersion\":4}"
                .getBytes(StandardCharsets.UTF_8);
        var secret = new byte[]{1, 2, 3};
        var update = OpenApiCanonicalRequest.canonical(
                "PUT", "/openapi/v1/modules/Purchase_order/records/42",
                null, updateBody, "1785196800", "nonce-record-update-01",
                "update-purchase-order-1");
        var changedUpdate = OpenApiCanonicalRequest.canonical(
                "PUT", "/openapi/v1/modules/Purchase_order/records/42",
                null, changedUpdateBody, "1785196800",
                "nonce-record-update-01", "update-purchase-order-1");
        var archive = OpenApiCanonicalRequest.canonical(
                "POST", "/openapi/v1/modules/Purchase_order/records/42:archive",
                null, lifecycleBody, "1785196800", "nonce-record-action-01",
                "action-purchase-order-1");
        var restore = OpenApiCanonicalRequest.canonical(
                "POST",
                "/openapi/v1/modules/Purchase_order/records/42:restore-from-trash",
                null, lifecycleBody, "1785196800", "nonce-record-action-01",
                "action-purchase-order-1");

        assertThat(update).startsWith("""
                PUT
                /openapi/v1/modules/Purchase_order/records/42

                """).doesNotContain("Sensitive Acme", "customer_name");
        assertThat(archive).contains("""
                /openapi/v1/modules/Purchase_order/records/42:archive

                """);
        assertThat(restore).contains("""
                /openapi/v1/modules/Purchase_order/records/42:restore-from-trash

                """);
        assertThat(OpenApiCanonicalRequest.signature(secret, update))
                .isNotEqualTo(OpenApiCanonicalRequest.signature(
                        secret, changedUpdate));
        assertThat(OpenApiCanonicalRequest.signature(secret, archive))
                .isNotEqualTo(OpenApiCanonicalRequest.signature(secret, restore));
    }

    @Test
    void signsFileJsonBodyAndExactListAndContentPaths() {
        var uploadBody = """
                {"originalName":"invoice.pdf","mediaType":"application/pdf","contentBase64":"UERG"}"""
                .getBytes(StandardCharsets.UTF_8);
        var changedUploadBody = """
                {"originalName":"invoice.pdf","mediaType":"application/pdf","contentBase64":"Q0hBTkdFRA=="}"""
                .getBytes(StandardCharsets.UTF_8);
        var secret = new byte[]{1, 2, 3};
        var upload = OpenApiCanonicalRequest.canonical(
                "POST", "/openapi/v1/modules/Purchase_order/records/42/files",
                null, uploadBody, "1785196800", "nonce-file-upload-001",
                "upload-purchase-order-file-1");
        var changedUpload = OpenApiCanonicalRequest.canonical(
                "POST", "/openapi/v1/modules/Purchase_order/records/42/files",
                null, changedUploadBody, "1785196800",
                "nonce-file-upload-001", "upload-purchase-order-file-1");
        var list = OpenApiCanonicalRequest.canonical(
                "GET", "/openapi/v1/modules/Purchase_order/records/42/files",
                "size=20&page=2", new byte[0], "1785196800",
                "nonce-file-list-0001", "file-list-1");
        var content = OpenApiCanonicalRequest.canonical(
                "GET",
                "/openapi/v1/modules/Purchase_order/records/42/files/17/content",
                null, new byte[0], "1785196800", "nonce-file-content-01",
                "file-content-1");

        assertThat(upload).startsWith("""
                POST
                /openapi/v1/modules/Purchase_order/records/42/files

                """).doesNotContain(
                        "invoice.pdf", "application/pdf", "contentBase64", "UERG");
        assertThat(list).contains("""
                /openapi/v1/modules/Purchase_order/records/42/files
                page=2&size=20
                """);
        assertThat(content).contains("""
                /openapi/v1/modules/Purchase_order/records/42/files/17/content

                """);
        assertThat(OpenApiCanonicalRequest.signature(secret, upload))
                .isNotEqualTo(OpenApiCanonicalRequest.signature(
                        secret, changedUpload));
        assertThat(OpenApiCanonicalRequest.signature(secret, list))
                .isNotEqualTo(OpenApiCanonicalRequest.signature(secret, content));
    }

    @Test
    void freezesSignedFlowInstanceStatusGet() {
        var first = OpenApiCanonicalRequest.canonical(
                "get", "/openapi/v1/flow/instances/42", null, new byte[0],
                "1785196800", "nonce-flow-status-001", "flow-status-read-1");
        var second = OpenApiCanonicalRequest.canonical(
                "GET", "/openapi/v1/flow/instances/43", null, new byte[0],
                "1785196800", "nonce-flow-status-001", "flow-status-read-1");

        assertThat(first).isEqualTo("""
                GET
                /openapi/v1/flow/instances/42

                e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
                1785196800
                nonce-flow-status-001
                flow-status-read-1""");
        assertThat(OpenApiCanonicalRequest.signature(
                new byte[]{1, 2, 3}, first))
                .isNotEqualTo(OpenApiCanonicalRequest.signature(
                        new byte[]{1, 2, 3}, second));
    }

    @Test
    void signsCompositionPagingPathAndMutationPayload() {
        var body = """
                {"expectedVersion":3,"targets":[{"recordId":"91","label":"Sensitive Acme"}]}"""
                .getBytes(StandardCharsets.UTF_8);
        var changedBody = """
                {"expectedVersion":3,"targets":[{"recordId":"92","label":"Sensitive Acme"}]}"""
                .getBytes(StandardCharsets.UTF_8);
        var secret = new byte[]{1, 2, 3};
        var relation = OpenApiCanonicalRequest.canonical(
                "GET",
                "/openapi/v1/modules/Purchase_order/records/42/"
                        + "relations/line_items",
                "size=20&page=2", new byte[0], "1785196800",
                "nonce-relation-read-01", "relation-read-1");
        var subtable = OpenApiCanonicalRequest.canonical(
                "GET",
                "/openapi/v1/modules/Purchase_order/records/42/"
                        + "subtables/delivery_rows",
                "size=20&page=2", new byte[0], "1785196800",
                "nonce-relation-read-01", "relation-read-1");
        var mutation = OpenApiCanonicalRequest.canonical(
                "POST",
                "/openapi/v1/modules/Purchase_order/records/42/"
                        + "relations/line_items:mutate",
                null, body, "1785196800", "nonce-relation-write1",
                "relation-write-1");
        var changedMutation = OpenApiCanonicalRequest.canonical(
                "POST",
                "/openapi/v1/modules/Purchase_order/records/42/"
                        + "relations/line_items:mutate",
                null, changedBody, "1785196800", "nonce-relation-write1",
                "relation-write-1");

        assertThat(relation).contains("""
                /openapi/v1/modules/Purchase_order/records/42/relations/line_items
                page=2&size=20
                """);
        assertThat(mutation).startsWith("""
                POST
                /openapi/v1/modules/Purchase_order/records/42/relations/line_items:mutate

                """).doesNotContain(
                        "recordId", "Sensitive Acme", "\"91\"");
        assertThat(OpenApiCanonicalRequest.signature(secret, relation))
                .isNotEqualTo(OpenApiCanonicalRequest.signature(secret, subtable));
        assertThat(OpenApiCanonicalRequest.signature(secret, mutation))
                .isNotEqualTo(OpenApiCanonicalRequest.signature(
                        secret, changedMutation));
    }
}
