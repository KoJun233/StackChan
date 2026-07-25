update wake_word_model_jobs
set status = 'FAILED',
    artifact = null,
    failure_code = 'feature_retired',
    updated_at = now()
where status in ('QUEUED', 'GENERATING', 'READY');

alter table wake_word_model_jobs
  drop constraint wake_word_model_jobs_source_check;

alter table wake_word_model_jobs
  drop column source;
