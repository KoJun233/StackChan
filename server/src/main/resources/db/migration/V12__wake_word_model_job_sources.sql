alter table wake_word_model_jobs
  add column source varchar(24) not null default 'ONLINE_GENERATION';

alter table wake_word_model_jobs
  add constraint wake_word_model_jobs_source_check
  check (source in ('ONLINE_GENERATION', 'UPLOAD'));

alter table wake_word_model_jobs
  alter column source drop default;
