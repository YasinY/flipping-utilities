-- Schema Version Tracking
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL
);

-- Accounts
CREATE TABLE IF NOT EXISTS account (
    display_name TEXT PRIMARY KEY,
    session_start_time TEXT,
    accumulated_session_time_millis INTEGER DEFAULT 0,
    last_session_time_update TEXT,
    last_stored_at TEXT,
    last_modified_at TEXT
);

-- Flipping Items
CREATE TABLE IF NOT EXISTS flipping_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_name TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    item_name TEXT NOT NULL,
    total_ge_limit INTEGER DEFAULT 0,
    flipped_by TEXT,
    valid_flipping_panel_item INTEGER DEFAULT 1,
    favorite INTEGER DEFAULT 0,
    favorite_code TEXT DEFAULT '1',
    next_ge_limit_refresh TEXT,
    items_bought_this_limit_window INTEGER DEFAULT 0,
    items_bought_through_complete_offers INTEGER DEFAULT 0,
    FOREIGN KEY (account_name) REFERENCES account(display_name) ON DELETE CASCADE,
    UNIQUE (account_name, item_id)
);

-- Offer Events
CREATE TABLE IF NOT EXISTS offer_event (
    uuid TEXT PRIMARY KEY,
    flipping_item_id INTEGER NOT NULL,
    is_buy INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    current_quantity_in_trade INTEGER NOT NULL,
    price INTEGER NOT NULL,
    time TEXT NOT NULL,
    slot INTEGER NOT NULL,
    state TEXT NOT NULL,
    tick_arrived_at INTEGER DEFAULT 0,
    ticks_since_first_offer INTEGER DEFAULT 0,
    total_quantity_in_trade INTEGER NOT NULL,
    trade_started_at TEXT,
    before_login INTEGER DEFAULT 0,
    FOREIGN KEY (flipping_item_id) REFERENCES flipping_item(id) ON DELETE CASCADE
);

-- Recipe Flip Groups
CREATE TABLE IF NOT EXISTS recipe_flip_group (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_name TEXT NOT NULL,
    recipe_name TEXT NOT NULL,
    recipe_data TEXT NOT NULL,
    FOREIGN KEY (account_name) REFERENCES account(display_name) ON DELETE CASCADE
);

-- Recipe Flips
CREATE TABLE IF NOT EXISTS recipe_flip (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recipe_flip_group_id INTEGER NOT NULL,
    time_of_creation TEXT NOT NULL,
    coin_cost INTEGER DEFAULT 0,
    FOREIGN KEY (recipe_flip_group_id) REFERENCES recipe_flip_group(id) ON DELETE CASCADE
);

-- Partial Offers
CREATE TABLE IF NOT EXISTS partial_offer (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recipe_flip_id INTEGER NOT NULL,
    offer_event_uuid TEXT NOT NULL,
    amount_consumed INTEGER NOT NULL,
    is_input INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    FOREIGN KEY (recipe_flip_id) REFERENCES recipe_flip(id) ON DELETE CASCADE,
    FOREIGN KEY (offer_event_uuid) REFERENCES offer_event(uuid) ON DELETE CASCADE
);

-- Last Offers
CREATE TABLE IF NOT EXISTS last_offer (
    account_name TEXT NOT NULL,
    slot INTEGER NOT NULL,
    offer_event_uuid TEXT NOT NULL,
    PRIMARY KEY (account_name, slot),
    FOREIGN KEY (account_name) REFERENCES account(display_name) ON DELETE CASCADE,
    FOREIGN KEY (offer_event_uuid) REFERENCES offer_event(uuid) ON DELETE CASCADE
);

-- Account Wide Data
CREATE TABLE IF NOT EXISTS account_wide_data (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    options_json TEXT,
    sections_json TEXT,
    local_recipes_json TEXT,
    should_make_new_additions INTEGER DEFAULT 1,
    enhanced_slots INTEGER DEFAULT 1,
    jwt TEXT
);

-- Slot Timers
CREATE TABLE IF NOT EXISTS slot_timer (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_name TEXT NOT NULL,
    slot_index INTEGER NOT NULL,
    timer_data TEXT,
    FOREIGN KEY (account_name) REFERENCES account(display_name) ON DELETE CASCADE,
    UNIQUE (account_name, slot_index)
);

-- Indices
CREATE INDEX IF NOT EXISTS idx_offer_event_flipping_item ON offer_event(flipping_item_id);
CREATE INDEX IF NOT EXISTS idx_offer_event_time ON offer_event(time);
CREATE INDEX IF NOT EXISTS idx_flipping_item_account ON flipping_item(account_name);
CREATE INDEX IF NOT EXISTS idx_partial_offer_recipe_flip ON partial_offer(recipe_flip_id);
