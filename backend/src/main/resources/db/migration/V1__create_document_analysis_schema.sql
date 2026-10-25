CREATE TABLE public.documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id uuid NOT NULL,
    title text NOT NULL,
    original_filename text NOT NULL,
    storage_object_path text NOT NULL,
    upload_idempotency_key text NOT NULL,
    upload_request_fingerprint text NOT NULL,
    mime_type text NOT NULL,
    size_bytes bigint NOT NULL,
    page_count integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_documents_owner
        FOREIGN KEY (owner_id) REFERENCES auth.users (id) ON DELETE CASCADE,
    CONSTRAINT uq_documents_storage_object_path UNIQUE (storage_object_path),
    CONSTRAINT uq_documents_owner_upload_key UNIQUE (owner_id, upload_idempotency_key),
    CONSTRAINT uq_documents_id_owner UNIQUE (id, owner_id),
    CONSTRAINT ck_documents_title_length CHECK (char_length(title) BETWEEN 1 AND 200),
    CONSTRAINT ck_documents_filename_length CHECK (char_length(original_filename) BETWEEN 1 AND 255),
    CONSTRAINT ck_documents_upload_key_length CHECK (char_length(upload_idempotency_key) BETWEEN 1 AND 128),
    CONSTRAINT ck_documents_mime_type CHECK (mime_type = 'application/pdf'),
    CONSTRAINT ck_documents_size CHECK (size_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT ck_documents_page_count CHECK (page_count BETWEEN 1 AND 30)
);

CREATE TABLE public.analysis_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'PENDING',
    attempt_number integer NOT NULL,
    idempotency_key text NOT NULL,
    request_fingerprint text NOT NULL,
    failure_code text,
    failure_message text,
    retry_count integer NOT NULL DEFAULT 0,
    max_retries integer NOT NULL,
    next_retry_at timestamptz,
    lease_owner text,
    lease_expires_at timestamptz,
    heartbeat_at timestamptz,
    deadline_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    completed_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_analysis_jobs_document_owner
        FOREIGN KEY (document_id, owner_id)
        REFERENCES public.documents (id, owner_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_analysis_jobs_owner_idempotency UNIQUE (owner_id, idempotency_key),
    CONSTRAINT uq_analysis_jobs_document_attempt UNIQUE (document_id, attempt_number),
    CONSTRAINT uq_analysis_jobs_identity UNIQUE (id, document_id, owner_id),
    CONSTRAINT ck_analysis_jobs_status CHECK (
        status IN ('PENDING', 'EXTRACTING', 'ANALYZING', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_analysis_jobs_attempt CHECK (attempt_number >= 1),
    CONSTRAINT ck_analysis_jobs_idempotency_key CHECK (char_length(idempotency_key) BETWEEN 1 AND 128),
    CONSTRAINT ck_analysis_jobs_retry_counts CHECK (
        retry_count >= 0 AND max_retries >= 0 AND retry_count <= max_retries
    ),
    CONSTRAINT ck_analysis_jobs_deadline CHECK (deadline_at > created_at),
    CONSTRAINT ck_analysis_jobs_terminal_timestamp CHECK (
        (status IN ('COMPLETED', 'FAILED') AND completed_at IS NOT NULL)
        OR (status IN ('PENDING', 'EXTRACTING', 'ANALYZING') AND completed_at IS NULL)
    ),
    CONSTRAINT ck_analysis_jobs_failure_details CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL AND failure_message IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL AND failure_message IS NULL)
    ),
    CONSTRAINT ck_analysis_jobs_lease_fields CHECK (
        (status IN ('EXTRACTING', 'ANALYZING')
            AND lease_owner IS NOT NULL
            AND lease_expires_at IS NOT NULL
            AND heartbeat_at IS NOT NULL)
        OR (status IN ('PENDING', 'COMPLETED', 'FAILED')
            AND lease_owner IS NULL
            AND lease_expires_at IS NULL
            AND heartbeat_at IS NULL)
    ),
    CONSTRAINT ck_analysis_jobs_next_retry CHECK (
        next_retry_at IS NULL OR status = 'PENDING'
    )
);

CREATE TABLE public.analysis_results (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_job_id uuid NOT NULL,
    document_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    result jsonb NOT NULL,
    model_name text NOT NULL,
    pipeline_version text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_analysis_results_job UNIQUE (analysis_job_id),
    CONSTRAINT fk_analysis_results_job_document_owner
        FOREIGN KEY (analysis_job_id, document_id, owner_id)
        REFERENCES public.analysis_jobs (id, document_id, owner_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_analysis_results_object CHECK (jsonb_typeof(result) = 'object'),
    CONSTRAINT ck_analysis_results_required_keys CHECK (
        result ?& ARRAY['schemaVersion', 'summary', 'keyPoints', 'keywords', 'warnings']
    ),
    CONSTRAINT ck_analysis_results_model_name CHECK (char_length(model_name) > 0),
    CONSTRAINT ck_analysis_results_pipeline_version CHECK (char_length(pipeline_version) > 0)
);

CREATE INDEX idx_documents_owner_created
    ON public.documents (owner_id, created_at DESC, id DESC);
CREATE INDEX idx_documents_owner_updated
    ON public.documents (owner_id, updated_at DESC, id DESC);
CREATE INDEX idx_documents_owner_title_sort
    ON public.documents (owner_id, lower(title), id);
CREATE INDEX idx_documents_owner_title_prefix
    ON public.documents (owner_id, (lower(title)) text_pattern_ops);

CREATE INDEX idx_analysis_jobs_document_history
    ON public.analysis_jobs (document_id, created_at DESC, id DESC);
CREATE INDEX idx_analysis_jobs_owner_status
    ON public.analysis_jobs (owner_id, status, created_at DESC);
CREATE UNIQUE INDEX uq_analysis_jobs_one_active_per_document
    ON public.analysis_jobs (document_id)
    WHERE status IN ('PENDING', 'EXTRACTING', 'ANALYZING');
CREATE INDEX idx_analysis_jobs_pending_queue
    ON public.analysis_jobs (status, next_retry_at, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_analysis_jobs_expired_lease
    ON public.analysis_jobs (lease_expires_at)
    WHERE status IN ('EXTRACTING', 'ANALYZING');

CREATE INDEX idx_analysis_results_document_history
    ON public.analysis_results (document_id, created_at DESC);
CREATE INDEX idx_analysis_results_keywords
    ON public.analysis_results USING gin ((result -> 'keywords'));

ALTER TABLE public.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.documents FORCE ROW LEVEL SECURITY;
ALTER TABLE public.analysis_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.analysis_jobs FORCE ROW LEVEL SECURITY;
ALTER TABLE public.analysis_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.analysis_results FORCE ROW LEVEL SECURITY;

CREATE POLICY documents_owner_all ON public.documents
    FOR ALL TO authenticated
    USING (owner_id = (SELECT auth.uid()))
    WITH CHECK (owner_id = (SELECT auth.uid()));

CREATE POLICY analysis_jobs_owner_all ON public.analysis_jobs
    FOR ALL TO authenticated
    USING (owner_id = (SELECT auth.uid()))
    WITH CHECK (
        owner_id = (SELECT auth.uid())
        AND EXISTS (
            SELECT 1
            FROM public.documents document
            WHERE document.id = analysis_jobs.document_id
              AND document.owner_id = (SELECT auth.uid())
        )
    );

CREATE POLICY analysis_results_owner_all ON public.analysis_results
    FOR ALL TO authenticated
    USING (owner_id = (SELECT auth.uid()))
    WITH CHECK (
        owner_id = (SELECT auth.uid())
        AND EXISTS (
            SELECT 1
            FROM public.analysis_jobs job
            WHERE job.id = analysis_results.analysis_job_id
              AND job.document_id = analysis_results.document_id
              AND job.owner_id = (SELECT auth.uid())
        )
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON public.documents TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.analysis_jobs TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.analysis_results TO authenticated;
