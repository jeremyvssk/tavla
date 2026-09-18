-- Demo catalog: chess and strategy games. A *repeatable* migration (R__), kept apart from the
-- versioned schema in db/migration: Flyway re-runs it whenever this file changes, which suits
-- content that is still being decided. Every insert is an upsert on a stable key, so a re-run
-- updates rows instead of duplicating them. For a completely clean catalog: ./start.sh reset.
-- Production would leave db/seed out of spring.flyway.locations.

-- Categories: three levels under Chess, two under Strategy Games.
INSERT INTO categories (name, slug, parent_id) VALUES
    ('Chess', 'chess', NULL),
    ('Strategy Games', 'strategy-games', NULL)
ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id;

INSERT INTO categories (name, slug, parent_id)
SELECT c.name, c.slug, p.id
FROM (VALUES
    ('Chess Sets',     'chess-sets',     'chess'),
    ('Chessboards',    'chessboards',    'chess'),
    ('Chess Pieces',   'chess-pieces',   'chess'),
    ('Chess Clocks',   'chess-clocks',   'chess'),
    ('Go',             'go',             'strategy-games'),
    ('Backgammon',     'backgammon',     'strategy-games')
) AS c(name, slug, parent_slug)
JOIN categories p ON p.slug = c.parent_slug
ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id;

INSERT INTO categories (name, slug, parent_id)
SELECT c.name, c.slug, p.id
FROM (VALUES
    ('Tournament Sets',    'tournament-sets', 'chess-sets'),
    ('Luxury Sets',        'luxury-sets',     'chess-sets'),
    ('Travel & Magnetic',  'travel-sets',     'chess-sets'),
    ('Wooden Boards',      'wooden-boards',   'chessboards'),
    ('Folding Boards',     'folding-boards',  'chessboards'),
    ('Roll-up Boards',     'roll-up-boards',  'chessboards'),
    ('Staunton Pieces',    'staunton-pieces', 'chess-pieces'),
    ('Themed Pieces',      'themed-pieces',   'chess-pieces')
) AS c(name, slug, parent_slug)
JOIN categories p ON p.slug = c.parent_slug
ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id;

-- Brands are invented, so no real company is attached to made-up products and reviews.
INSERT INTO brands (name, slug) VALUES
    ('Rank & File',        'rank-and-file'),
    ('Old Oak Workshop',   'old-oak-workshop'),
    ('Baltic Boardworks',  'baltic-boardworks'),
    ('Nordic Gambit',      'nordic-gambit'),
    ('Kingside Clocks',    'kingside-clocks'),
    ('Stone Tile Games',   'stone-tile-games'),
    ('Pocket Pawn',        'pocket-pawn'),
    ('Ebony & Box',        'ebony-and-box')
ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name;

-- Reviewers. The password hash is not a BCrypt string, so no password can ever log in as them.
INSERT INTO users (id, email, password_hash, full_name, auth_provider, role) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'reviewer-1@seed.iloveshopping.local', '!seed-no-login', 'Mari K.',    'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000002', 'reviewer-2@seed.iloveshopping.local', '!seed-no-login', 'Jaan T.',    'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000003', 'reviewer-3@seed.iloveshopping.local', '!seed-no-login', 'Liis P.',    'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000004', 'reviewer-4@seed.iloveshopping.local', '!seed-no-login', 'Andres M.',  'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000005', 'reviewer-5@seed.iloveshopping.local', '!seed-no-login', 'Kadri S.',   'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000006', 'reviewer-6@seed.iloveshopping.local', '!seed-no-login', 'Toomas R.',  'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000007', 'reviewer-7@seed.iloveshopping.local', '!seed-no-login', 'Eva L.',     'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000008', 'reviewer-8@seed.iloveshopping.local', '!seed-no-login', 'Martin O.',  'LOCAL', 'CUSTOMER')
ON CONFLICT (id) DO NOTHING;

-- quality (1-5) steers the generated review ratings below; it is not stored on the product.
CREATE TEMP TABLE seed_product (
    n INT, name TEXT, category TEXT, brand TEXT, price NUMERIC, stock INT, quality INT,
    active BOOLEAN, weight_kg NUMERIC, width_cm NUMERIC, height_cm NUMERIC, depth_cm NUMERIC,
    attributes JSONB, description TEXT
) ON COMMIT DROP;

INSERT INTO seed_product VALUES
-- Tournament sets
(1, 'Club Tournament Chess Set', 'tournament-sets', 'rank-and-file', 39.90, 120, 4, true, 1.2, 51, 6, 10,
 '{"style":"Staunton","material":"plastic","weighted":true,"king_height_mm":95,"square_mm":57}',
 'Weighted plastic Staunton pieces with a 95 mm king and a green and buff roll-up board. The set you find at most club tournaments.'),
(2, 'Tournament Set with Stained Beech Box', 'tournament-sets', 'rank-and-file', 69.00, 45, 4, true, 1.9, 30, 8, 12,
 '{"style":"Staunton","material":"plastic","weighted":true,"king_height_mm":95,"square_mm":57}',
 'Double-weighted pieces stored in a sliding beech box, with a folding vinyl board.'),
(3, 'Olympiad Weighted Staunton Set', 'tournament-sets', 'nordic-gambit', 89.00, 30, 5, true, 2.1, 52, 6, 52,
 '{"style":"Staunton","wood":["boxwood","sheesham"],"weighted":true,"king_height_mm":95,"square_mm":57}',
 'Boxwood and sheesham pieces with felted bases, sized for a 57 mm tournament board.'),
(4, 'School Chess Set, Pack of 10', 'tournament-sets', 'rank-and-file', 229.00, 12, 3, true, 9.5, 55, 30, 40,
 '{"style":"Staunton","material":"plastic","weighted":false,"king_height_mm":95,"square_mm":51}',
 'Ten complete plastic sets with roll-up boards and storage bags, for clubs and classrooms.'),
(5, 'Triple-Weighted Club Set', 'tournament-sets', 'nordic-gambit', 119.00, 0, 4, true, 2.6, 52, 6, 52,
 '{"style":"Staunton","material":"plastic","weighted":true,"king_height_mm":95,"square_mm":57}',
 'Heavy pieces that stay put in blitz, with a silicone board that rolls flat without curling.'),
(6, 'Folding Tournament Set, Beech', 'tournament-sets', 'baltic-boardworks', 79.99, 25, 3, true, 2.3, 50, 5, 25,
 '{"style":"Staunton","wood":["beech"],"weighted":false,"king_height_mm":89,"square_mm":50}',
 'A folding beech board that doubles as the storage case for its matching pieces.'),
(7, 'Staunton Set with Walnut Finish Board', 'tournament-sets', 'nordic-gambit', 99.00, 18, 4, true, 3.1, 45, 3, 45,
 '{"style":"Staunton","wood":["walnut","maple"],"weighted":true,"king_height_mm":89,"square_mm":50}',
 'Weighted pieces on a walnut finish board with maple squares and a satin lacquer.'),
-- Luxury sets
(8, 'Ebony and Boxwood Imperial Set', 'luxury-sets', 'ebony-and-box', 649.00, 4, 5, true, 5.8, 60, 4, 60,
 '{"style":"Staunton","wood":["ebony","boxwood"],"weighted":true,"king_height_mm":102,"square_mm":60}',
 'Hand-carved knights in genuine ebony and boxwood, triple weighted, with a presentation case.'),
(9, 'Rosewood Staunton Set with Walnut Board', 'luxury-sets', 'old-oak-workshop', 389.00, 6, 5, true, 4.9, 55, 4, 55,
 '{"style":"Staunton","wood":["rosewood","boxwood","walnut"],"weighted":true,"king_height_mm":95,"square_mm":55}',
 'Rosewood pieces paired with a solid walnut and maple board, chosen so the tones match.'),
(10, 'Dubrovnik 1950 Reproduction Set', 'luxury-sets', 'ebony-and-box', 459.00, 3, 4, true, 3.2, 30, 12, 20,
 '{"style":"Dubrovnik","wood":["beech"],"weighted":true,"king_height_mm":98,"square_mm":58}',
 'A reproduction of the design made for the 1950 Dubrovnik Olympiad, turned in beech.'),
(11, 'Lardy Pattern Set in Padauk', 'luxury-sets', 'old-oak-workshop', 329.00, 5, 4, true, 2.8, 30, 12, 20,
 '{"style":"Lardy","wood":["padauk","boxwood"],"weighted":true,"king_height_mm":97,"square_mm":57}',
 'French Lardy pattern pieces in warm padauk, a slimmer silhouette than Staunton.'),
(12, 'Burnt Boxwood Heirloom Set', 'luxury-sets', 'old-oak-workshop', 279.00, 8, 4, true, 2.4, 30, 12, 20,
 '{"style":"Staunton","wood":["boxwood"],"weighted":true,"king_height_mm":95,"square_mm":57}',
 'Boxwood pieces darkened by flame instead of stain, so the grain stays visible.'),
(13, 'Black and White Marble Chess Set', 'luxury-sets', 'ebony-and-box', 199.00, 10, 3, true, 7.5, 40, 5, 40,
 '{"style":"Modern","material":"marble","weighted":true,"king_height_mm":80,"square_mm":45}',
 'Polished marble board and pieces. Heavy, cold to the touch, and hard to knock over.'),
(14, 'Walnut Chess Table with Drawer', 'luxury-sets', 'old-oak-workshop', 890.00, 2, 5, true, 21.0, 65, 72, 65,
 '{"style":"Staunton","wood":["walnut","maple"],"weighted":true,"king_height_mm":95,"square_mm":57}',
 'A solid walnut side table with an inlaid board and a felt-lined drawer for the pieces.'),
(15, 'Weighted Walnut Staunton Set', 'luxury-sets', 'nordic-gambit', 179.00, 14, 5, true, 3.4, 50, 4, 50,
 '{"style":"Staunton","wood":["walnut","boxwood"],"weighted":true,"king_height_mm":95,"square_mm":55}',
 'Walnut-stained and natural boxwood pieces with a matching board.'),
(16, 'Discontinued Brass Chess Set', 'luxury-sets', 'ebony-and-box', 399.00, 0, 3, false, 6.0, 40, 5, 40,
 '{"style":"Modern","material":"brass","weighted":true,"king_height_mm":85,"square_mm":50}',
 'No longer sold. Kept to show that inactive products stay out of search and browse.'),
-- Travel and magnetic sets
(17, 'Magnetic Pocket Chess, 18 cm', 'travel-sets', 'pocket-pawn', 14.90, 200, 3, true, 0.3, 18, 2, 18,
 '{"style":"Staunton","material":"plastic","magnetic":true,"square_mm":20}',
 'Flat magnetic pieces in a board that folds to the size of a paperback.'),
(18, 'Magnetic Travel Set, 30 cm', 'travel-sets', 'pocket-pawn', 24.90, 150, 4, true, 0.7, 30, 3, 15,
 '{"style":"Staunton","material":"plastic","magnetic":true,"king_height_mm":45,"square_mm":32}',
 'Folding magnetic board with storage slots, for trains, planes and waiting rooms.'),
(19, 'Roll-up Travel Set with Carry Tube', 'travel-sets', 'rank-and-file', 29.90, 80, 4, true, 1.1, 10, 55, 10,
 '{"style":"Staunton","material":"plastic","weighted":true,"king_height_mm":95,"square_mm":57}',
 'Full-size tournament pieces and a vinyl board in a shoulder tube.'),
(20, 'Leather Wallet Chess Set', 'travel-sets', 'pocket-pawn', 34.00, 40, 2, true, 0.2, 12, 2, 18,
 '{"material":"leather","magnetic":true,"square_mm":14}',
 'A leather wallet with a tiny magnetic board. Charming, but the pieces are fiddly.'),
(21, 'Wooden Magnetic Folding Set, 25 cm', 'travel-sets', 'baltic-boardworks', 44.00, 60, 3, true, 0.9, 25, 4, 13,
 '{"style":"Staunton","wood":["birch"],"magnetic":true,"king_height_mm":50,"square_mm":27}',
 'Birch pieces with magnets set into the base, in a board that folds shut.'),
(22, 'Walnut Chess Box with Pieces', 'chess-sets', 'baltic-boardworks', 129.00, 11, 3, true, 1.8, 30, 7, 30,
 '{"style":"Staunton","wood":["walnut","birch"],"weighted":false,"king_height_mm":76,"square_mm":35}',
 'A walnut box with a board on the lid and a compartment for each colour.'),
(23, 'Chess Set for Kids with Piece Guide', 'chess-sets', 'rank-and-file', 24.00, 70, 4, true, 1.0, 36, 5, 36,
 '{"style":"Staunton","material":"plastic","weighted":false,"king_height_mm":76,"square_mm":40}',
 'Each piece shows how it moves on its base, so beginners can play from the first game.'),
-- Wooden boards
(24, 'Walnut and Maple Chessboard, 55 mm', 'wooden-boards', 'baltic-boardworks', 149.00, 20, 5, true, 3.0, 50, 2, 50,
 '{"wood":["walnut","maple"],"square_mm":55,"notation":false}',
 'Solid walnut and maple squares with a 25 mm border, oiled rather than lacquered.'),
(25, 'Rosewood and Maple Chessboard, 57 mm', 'wooden-boards', 'old-oak-workshop', 219.00, 9, 4, true, 3.4, 53, 2, 53,
 '{"wood":["rosewood","maple"],"square_mm":57,"notation":true}',
 'Rosewood squares with algebraic notation burnt into a maple border.'),
(26, 'Birch Veneer Club Board, 50 mm', 'wooden-boards', 'baltic-boardworks', 49.00, 55, 3, true, 1.6, 45, 1, 45,
 '{"wood":["birch"],"square_mm":50,"notation":false}',
 'Light birch veneer over plywood. Sturdy and cheap enough for a whole club.'),
(27, 'Wenge and Sycamore Board, 60 mm', 'wooden-boards', 'old-oak-workshop', 259.00, 5, 4, true, 4.1, 56, 2, 56,
 '{"wood":["wenge","sycamore"],"square_mm":60,"notation":false}',
 'High contrast dark wenge and pale sycamore, sized for 102 mm kings.'),
(28, 'Ash Chessboard with Notation, 45 mm', 'wooden-boards', 'baltic-boardworks', 89.00, 22, 4, true, 2.2, 42, 2, 42,
 '{"wood":["ash","walnut"],"square_mm":45,"notation":true}',
 'Ash and walnut with file and rank letters, for studying games from books.'),
(29, 'Oak and Blue Resin River Chessboard', 'wooden-boards', 'baltic-boardworks', 340.00, 3, 4, true, 5.2, 52, 3, 52,
 '{"wood":["oak"],"material":"epoxy resin","square_mm":57,"notation":false}',
 'A decor board: a blue resin river runs through oak squares. Made to hang on a wall between games.'),
-- Folding boards
(30, 'Folding Beech Chessboard, 45 mm', 'folding-boards', 'baltic-boardworks', 39.00, 90, 3, true, 1.4, 42, 2, 42,
 '{"wood":["beech"],"square_mm":45,"folding":true}',
 'Folds in half with a brass hinge. The squares meet cleanly in the middle.'),
(31, 'Folding Walnut Board with Storage', 'folding-boards', 'old-oak-workshop', 119.00, 16, 4, true, 2.5, 40, 6, 40,
 '{"wood":["walnut","maple"],"square_mm":45,"folding":true}',
 'Opens into a board, closes into a case with felt-lined compartments.'),
(32, 'Folding Tournament Board, 57 mm', 'folding-boards', 'rank-and-file', 59.00, 35, 4, true, 1.9, 55, 1, 55,
 '{"material":"vinyl on board","square_mm":57,"folding":true,"notation":true}',
 'A rigid folding board in tournament green and buff.'),
-- Roll-up boards
(33, 'Green Vinyl Roll-up Board, 57 mm', 'roll-up-boards', 'rank-and-file', 9.90, 300, 4, true, 0.3, 51, 1, 51,
 '{"material":"vinyl","square_mm":57,"notation":true}',
 'The classic tournament mat in green and buff.'),
(34, 'Silicone Roll-up Board, Blue', 'roll-up-boards', 'rank-and-file', 16.50, 140, 3, true, 0.4, 51, 1, 51,
 '{"material":"silicone","square_mm":57,"notation":true}',
 'Rolls tight, lies flat, and survives a spilled coffee.'),
(35, 'Mousepad Chessboard, 51 mm', 'roll-up-boards', 'pocket-pawn', 19.90, 75, 2, true, 0.5, 48, 1, 48,
 '{"material":"neoprene","square_mm":51,"notation":true}',
 'Soft neoprene with a rubber base. The squares are slightly small for 95 mm kings.'),
-- Staunton pieces
(36, 'Weighted Boxwood Staunton Pieces, 95 mm King', 'staunton-pieces', 'nordic-gambit', 129.00, 26, 5, true, 1.4, 25, 10, 15,
 '{"style":"Staunton","wood":["boxwood","ebonised boxwood"],"weighted":true,"king_height_mm":95}',
 'Pieces only. Pair with a 55 to 60 mm square board.'),
(37, 'Ebonised Staunton Pieces, 89 mm King', 'staunton-pieces', 'nordic-gambit', 99.00, 30, 4, true, 1.1, 25, 10, 15,
 '{"style":"Staunton","wood":["ebonised boxwood","boxwood"],"weighted":true,"king_height_mm":89}',
 'Ebonised and natural boxwood. Pair with a 50 to 55 mm square board.'),
(38, 'Sheesham Staunton Pieces, 76 mm King', 'staunton-pieces', 'rank-and-file', 59.00, 44, 3, true, 0.8, 22, 9, 14,
 '{"style":"Staunton","wood":["sheesham","boxwood"],"weighted":false,"king_height_mm":76}',
 'Compact pieces for 40 to 45 mm squares.'),
(39, 'Plastic Club Pieces with Extra Queens', 'staunton-pieces', 'rank-and-file', 12.90, 250, 4, true, 0.5, 20, 8, 12,
 '{"style":"Staunton","material":"plastic","weighted":false,"king_height_mm":95}',
 'Standard club pieces, with a spare queen of each colour for promotions.'),
(40, 'Rosewood Staunton Pieces, 102 mm King', 'staunton-pieces', 'ebony-and-box', 299.00, 7, 5, true, 1.9, 28, 12, 16,
 '{"style":"Staunton","wood":["rosewood","boxwood"],"weighted":true,"king_height_mm":102}',
 'Large, heavy pieces for 60 mm squares.'),
(41, 'Tall Staunton Pieces for Walnut Boards', 'staunton-pieces', 'old-oak-workshop', 159.00, 12, 4, true, 1.5, 26, 11, 16,
 '{"style":"Staunton","wood":["boxwood","walnut"],"weighted":true,"king_height_mm":98}',
 'Walnut and boxwood pieces whose tones are matched to our walnut boards.'),
(42, 'Replacement Pawns, Set of 16', 'staunton-pieces', NULL, 8.50, 500, 3, true, 0.2, 10, 5, 10,
 '{"style":"Staunton","material":"plastic","king_height_mm":95}',
 'Eight white and eight black pawns for club sets that lose them.'),
-- Themed pieces
(43, 'Viking Themed Chess Pieces, Resin', 'themed-pieces', 'nordic-gambit', 74.00, 21, 3, true, 1.3, 25, 10, 15,
 '{"style":"Themed","material":"resin","theme":"Viking","king_height_mm":90}',
 'Hand-painted resin warriors. More for the shelf than for blitz.'),
(44, 'Hand-Painted Medieval Pieces', 'themed-pieces', 'nordic-gambit', 139.00, 9, 4, true, 1.4, 25, 10, 15,
 '{"style":"Themed","material":"resin","theme":"Medieval","king_height_mm":92}',
 'Knights, bishops and castles painted by hand, each set slightly different.'),
(45, 'Minimalist Cylinder Pieces, Oak', 'themed-pieces', 'old-oak-workshop', 89.00, 15, 3, true, 0.9, 22, 8, 14,
 '{"style":"Modern","wood":["oak","smoked oak"],"weighted":false,"king_height_mm":70}',
 'Abstract turned shapes that read clearly once you learn them. Designed to match a Scandinavian room.'),
(46, 'Brass and Pewter Art Deco Pieces', 'themed-pieces', 'ebony-and-box', 219.00, 6, 4, true, 2.2, 26, 10, 16,
 '{"style":"Art Deco","material":"brass","weighted":true,"king_height_mm":88}',
 'Metal pieces with stepped Art Deco profiles, in brass and pewter.'),
-- Chess clocks
(47, 'Digital Chess Clock with Increment', 'chess-clocks', 'kingside-clocks', 49.00, 60, 4, true, 0.4, 18, 6, 9,
 '{"type":"digital","increment":true,"delay":true}',
 'Fischer increment and Bronstein delay, with large buttons for blitz.'),
(48, 'Tournament Digital Clock, FIDE Modes', 'chess-clocks', 'kingside-clocks', 89.00, 25, 5, true, 0.5, 20, 7, 10,
 '{"type":"digital","increment":true,"delay":true,"fide_approved_modes":true}',
 'Preset time controls for FIDE events, including move-count bonuses.'),
(49, 'Analogue Wooden Chess Clock', 'chess-clocks', 'kingside-clocks', 69.00, 18, 3, true, 0.7, 20, 11, 8,
 '{"type":"analogue","wood":["beech"],"flag":true}',
 'Mechanical movement with a falling flag. Ticks audibly, which some players love.'),
(50, 'Compact Travel Chess Clock', 'chess-clocks', 'kingside-clocks', 29.00, 45, 2, true, 0.2, 12, 4, 6,
 '{"type":"digital","increment":false}',
 'Small and light. The buttons are too stiff for serious blitz.'),
(51, 'Walnut Analogue Clock with Flag', 'chess-clocks', 'kingside-clocks', 149.00, 7, 4, true, 0.9, 22, 12, 9,
 '{"type":"analogue","wood":["walnut"],"flag":true}',
 'A mechanical clock in a walnut case that suits a wooden board.'),
-- Go
(52, 'Beginner Go Set, 9x9 and 13x13', 'go', 'stone-tile-games', 34.00, 50, 4, true, 1.8, 40, 3, 40,
 '{"board_size":"9x9 and 13x13","stones":"plastic","wood":["bamboo"]}',
 'A reversible bamboo board with small grids for learning, and plastic stones.'),
(53, 'Kaya-Style Go Board, 19x19', 'go', 'stone-tile-games', 189.00, 8, 4, true, 6.5, 45, 6, 42,
 '{"board_size":"19x19","wood":["spruce"]}',
 'A 6 cm spruce board with a hand-drawn grid, in the style of traditional kaya boards.'),
(54, 'Glass Go Stones in Bamboo Bowls', 'go', 'stone-tile-games', 59.00, 30, 4, true, 2.4, 30, 9, 15,
 '{"stones":"glass","stone_diameter_mm":22}',
 '361 double-convex glass stones with two turned bamboo bowls.'),
(55, 'Yunzi Go Stones, 22 mm', 'go', 'stone-tile-games', 119.00, 12, 5, true, 2.8, 30, 9, 15,
 '{"stones":"yunzi","stone_diameter_mm":22}',
 'Sintered Yunzi stones with a soft matte finish and a satisfying click.'),
(56, 'Folding Go Board, Spruce', 'go', 'stone-tile-games', 69.00, 20, 3, true, 2.0, 45, 2, 42,
 '{"board_size":"19x19","wood":["spruce"],"folding":true}',
 'A thin folding board for playing away from home.'),
-- Backgammon
(57, 'Leather Backgammon Set, 45 cm', 'backgammon', 'stone-tile-games', 159.00, 10, 4, true, 3.0, 45, 8, 30,
 '{"material":"leather","size_cm":45,"doubling_cube":true}',
 'Stitched leather case with felt playing field and weighted checkers.'),
(58, 'Magnetic Travel Backgammon', 'backgammon', 'pocket-pawn', 22.00, 65, 3, true, 0.5, 25, 3, 15,
 '{"material":"plastic","magnetic":true,"size_cm":25}',
 'Magnetic checkers that stay in place when the train brakes.'),
(59, 'Inlaid Olive Wood Backgammon Board', 'backgammon', 'old-oak-workshop', 249.00, 4, 5, true, 4.2, 50, 8, 32,
 '{"wood":["olive","walnut"],"size_cm":50,"doubling_cube":true}',
 'Olive wood points inlaid into walnut, with a matching doubling cube.'),
(60, 'Tournament Backgammon with Doubling Cube', 'backgammon', 'stone-tile-games', 299.00, 5, 4, true, 4.8, 53, 9, 35,
 '{"material":"leather","size_cm":53,"doubling_cube":true,"precision_dice":true}',
 'Full-size tournament board with precision dice and dice cups.'),
(61, 'Chess, Checkers and Backgammon 3-in-1', 'strategy-games', 'baltic-boardworks', 54.00, 40, 3, true, 2.1, 38, 5, 38,
 '{"wood":["birch"],"games":["chess","checkers","backgammon"]}',
 'A birch box with a chessboard on the outside and backgammon inside.');

INSERT INTO products (id, name, description, price, stock_quantity, category_id, brand_id, attributes, active,
                      weight_kg, width_cm, height_cm, depth_cm, weight_lbs, width_in, height_in, depth_in, created_at, updated_at)
SELECT md5('seed-product:' || s.name)::uuid,
       s.name, s.description, s.price, s.stock, c.id, b.id, s.attributes, s.active,
       s.weight_kg, s.width_cm, s.height_cm, s.depth_cm,
       -- Same conversion ProductService applies on every write: metric is the source of truth.
       round(s.weight_kg * 2.20462262, 2), round(s.width_cm / 2.54, 2), round(s.height_cm / 2.54, 2), round(s.depth_cm / 2.54, 2),
       now() - make_interval(days => s.n * 3), now()
FROM seed_product s
JOIN categories c ON c.slug = s.category
LEFT JOIN brands b ON b.slug = s.brand
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, description = EXCLUDED.description, price = EXCLUDED.price,
    stock_quantity = EXCLUDED.stock_quantity, category_id = EXCLUDED.category_id, brand_id = EXCLUDED.brand_id,
    attributes = EXCLUDED.attributes, active = EXCLUDED.active,
    weight_kg = EXCLUDED.weight_kg, width_cm = EXCLUDED.width_cm, height_cm = EXCLUDED.height_cm, depth_cm = EXCLUDED.depth_cm,
    weight_lbs = EXCLUDED.weight_lbs, width_in = EXCLUDED.width_in, height_in = EXCLUDED.height_in, depth_in = EXCLUDED.depth_in,
    updated_at = now();

-- Reviews, generated deterministically: each product gets 0-8 reviewers and ratings scattered
-- around its quality. hashtext gives stable pseudo-randomness, so a re-run yields the same rows.
-- The V6 trigger fills in average_rating and review_count as these land.
INSERT INTO product_reviews (id, product_id, user_id, rating, title, body, verified_purchase, created_at)
SELECT md5(p.id::text || u.id::text)::uuid,
       p.id, u.id, r.rating,
       (ARRAY['Disappointed', 'Not for me', 'Does the job', 'Very happy with it', 'Exactly what I hoped for'])[r.rating],
       (ARRAY[
           'The quality did not match the photos and I sent it back.',
           'Usable, but I expected better finishing for the price.',
           'Fine for casual games. Nothing special, nothing wrong.',
           'Well made and it looks good on the shelf. Would buy again.',
           'Beautiful piece. It gets compliments from everyone who visits.'
       ])[r.rating],
       false,
       now() - make_interval(days => (h.pair % 200)::int)
FROM seed_product s
JOIN products p ON p.id = md5('seed-product:' || s.name)::uuid
CROSS JOIN (SELECT id, row_number() OVER (ORDER BY id) AS k FROM users WHERE email LIKE '%@seed.iloveshopping.local') u
CROSS JOIN LATERAL (SELECT hashtext(p.id::text)::bigint & 2147483647 AS product,
                           hashtext(p.id::text || u.id::text)::bigint & 2147483647 AS pair) h
CROSS JOIN LATERAL (SELECT greatest(1, least(5, s.quality + (h.pair % 3)::int - 1)) AS rating) r
WHERE u.k <= h.product % 9
ON CONFLICT (id) DO UPDATE SET rating = EXCLUDED.rating, title = EXCLUDED.title, body = EXCLUDED.body;

DROP TABLE seed_product;
