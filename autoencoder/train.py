import argparse

from model.autoencoder import AutoencoderService


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train histology autoencoder")
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--learning-rate", type=float, default=0.0005)
    parser.add_argument("--image-size", type=int, default=256)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    service = AutoencoderService()
    result = service.train_model(
        epochs=args.epochs,
        batch_size=args.batch_size,
        learning_rate=args.learning_rate,
        image_size=args.image_size,
    )
    print(result["message"])


if __name__ == "__main__":
    main()
