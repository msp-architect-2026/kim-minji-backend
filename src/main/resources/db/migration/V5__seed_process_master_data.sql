INSERT INTO process_step (code, name, seq) VALUES
    ('PHOTO', 'Photolithography', 10),
    ('ETCH', 'Etching', 20),
    ('DEPO', 'Deposition', 30),
    ('CMP', 'Chemical Mechanical Polishing', 40);

INSERT INTO equipment (code, type, status) VALUES
    ('PHOTO-01', 'PHOTO', 'RUN'),
    ('PHOTO-02', 'PHOTO', 'RUN'),
    ('ETCH-01', 'ETCH', 'RUN'),
    ('ETCH-02', 'ETCH', 'RUN'),
    ('ETCH-03', 'ETCH', 'RUN'),
    ('DEPO-01', 'DEPO', 'RUN'),
    ('DEPO-02', 'DEPO', 'RUN'),
    ('CMP-01', 'CMP', 'RUN'),
    ('CMP-02', 'CMP', 'RUN');
