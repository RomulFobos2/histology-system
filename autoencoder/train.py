from model.autoencoder import AutoencoderService


def main() -> None:
    service = AutoencoderService()
    result = service.train_model(epochs=15, batch_size=4, learning_rate=0.0005, image_size=256)
    print(result["message"])


if __name__ == "__main__":
    main()
