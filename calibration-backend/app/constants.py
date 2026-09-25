# One entry per supported game: the ONNX file (under WEIGHTS_DIR) produced by
# training/export_model.py, and the class names in model output order.
#
# Class order MUST match the game's training dataset YAML `names` — the model
# outputs class indices, and these lists are what turn them back into labels.
# If you change one, change both.
GAMES = {
    # training/mlbb_data.yaml
    "mlbb": {
        "weights": "mlbb.onnx",
        "class_names": [
            "recall",
            "regen",
            "spell",
            "buy_item",
            "use_item",
            "basic_attack",
            "skill_button",
            "skill_upgrade",
            "joystick",
        ],
    },
    # training/clash_royale_data.yaml
    "clash_royale": {
        "weights": "clash_royale.onnx",
        "class_names": [
            "card",
            "champion_ability",
        ],
    },
}

DEFAULT_GAME = "mlbb"
