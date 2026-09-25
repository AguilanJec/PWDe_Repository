# Class order MUST match training/mlbb_data.yaml `names` — the model outputs
# class indices, and this list is what turns them back into labels.
# If you change one, change both.
CLASS_NAMES = [
    "recall",
    "regen",
    "spell",
    "buy_item",
    "use_item",
    "basic_attack",
    "skill_button",
    "skill_upgrade",
    "joystick",
]
