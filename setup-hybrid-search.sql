-- Setup script for Oracle Hybrid Vector Search
-- Run once after the Oracle container is running:
--   podman cp all_MiniLM_L12_v2.onnx oradb:/opt/oracle/dumps/
--   podman exec -i oradb sqlplus pdbadmin/Oracle123@freepdb1 < setup-hybrid-search.sql

-- 1. Create directory object for ONNX model file
CREATE OR REPLACE DIRECTORY DM_DUMP AS '/opt/oracle/dumps';

-- 2. Load the pre-built ONNX embedding model
BEGIN
  DBMS_VECTOR.LOAD_ONNX_MODEL(
    directory  => 'DM_DUMP',
    file_name  => 'all_MiniLM_L12_v2.onnx',
    model_name => 'ALL_MINILM_L12_V2'
  );
END;
/

-- 3. Verify the model works
SELECT VECTOR_EMBEDDING(ALL_MINILM_L12_V2 USING 'hello world' AS data) FROM DUAL;

-- 4. Create the policy documents table
CREATE TABLE IF NOT EXISTS POLICY_DOCS (
  id      VARCHAR2(36) DEFAULT sys_guid() PRIMARY KEY,
  content CLOB NOT NULL
);

-- 5. Create the hybrid vector index (vector + Oracle Text keyword search)
CREATE HYBRID VECTOR INDEX POLICY_HYBRID_IDX
ON POLICY_DOCS(content)
PARAMETERS('MODEL ALL_MINILM_L12_V2 VECTOR_IDXTYPE HNSW');
