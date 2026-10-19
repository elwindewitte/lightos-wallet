-- Mock loyalty cards for testing and screenshots. Fake numbers, real-looking names.
-- EAN-13 / EAN-8 / UPC-A values carry correct check digits, so they render and scan.
-- Load with: ./scripts/load-mock-cards.sh   (REPLACES every card on the emulator)
BEGIN;
DELETE FROM cards;
INSERT INTO cards(name, code, codeType) VALUES ('Albert Heijn', '2620000012346',          'EAN_13');
INSERT INTO cards(name, code, codeType) VALUES ('ANWB',         'ANWB-123456',            'CODE_39');
INSERT INTO cards(name, code, codeType) VALUES ('Cineville',    'CINEVILLE-NL-000123456', 'QR');
INSERT INTO cards(name, code, codeType) VALUES ('Etos',         '80001232',               'EAN_8');
INSERT INTO cards(name, code, codeType) VALUES ('HEMA',         'HEMA0012345678',         'CODE_128');
INSERT INTO cards(name, code, codeType) VALUES ('IKEA Family',  '6275980012345678',       'CODE_128');
INSERT INTO cards(name, code, codeType) VALUES ('Jumbo',        '2600000456784',          'EAN_13');
INSERT INTO cards(name, code, codeType) VALUES ('Kruidvat',     '8712345000127',          'EAN_13');
INSERT INTO cards(name, code, codeType) VALUES ('Museumkaart',  'MK-1234-5678',           'CODE_128');
INSERT INTO cards(name, code, codeType) VALUES ('Praxis',       '040000123453',           'UPC_A');
COMMIT;
