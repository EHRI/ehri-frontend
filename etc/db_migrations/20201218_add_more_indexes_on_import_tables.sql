CREATE INDEX harvest_event_repo_dataset ON harvest_event(repo_id, import_dataset_id);
CREATE INDEX harvest_event_user ON harvest_event(user_id);
 
CREATE INDEX import_log_repo_dataset ON import_log(repo_id, import_dataset_id);
CREATE INDEX import_file_mapping_import_log_id ON import_file_mapping (import_log_id);
CREATE INDEX import_file_mapping_key ON import_file_mapping (key);
CREATE INDEX import_file_mapping_item_id ON import_file_mapping (item_id);
CREATE INDEX import_file_mapping_import_log_id_item_id ON import_file_mapping(import_log_id, item_id);

