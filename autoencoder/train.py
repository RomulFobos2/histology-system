from model.autoencoder import AutoencoderService


def main() -> None:
    service = AutoencoderService()
    result = service.train_stub(epochs=10, batch_size=8, learning_rate=0.001)
    print(result["message"])


if __name__ == "__main__":
    main()
