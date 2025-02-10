class SplashScene extends Phaser.Scene {
  constructor() {
    super({ key: 'SplashScene' });
  }

  preload() {
    this.load.image('bulifierIcon', 'assets/images/bulifier-logo.png');
  }

  create() {
    const { width, height } = this.cameras.main;

    // Position the splash text at one-third of the screen height.
    const textY = height / 6;
    this.add.text(width / 2, textY, 'Built with Bulifier', {
      fontFamily: 'SpecialFont',
      fontSize: '48px',
      color: '#ffffff'
    }).setOrigin(0.5);

    // Calculate the desired width (80% of the screen width) for the image.
    const desiredWidth = width * 0.8;
    // Retrieve the original image dimensions.
    const textureSource = this.textures.get('bulifierIcon').getSourceImage();
    const origWidth = textureSource.width;
    const origHeight = textureSource.height;

    // Calculate scale factor and corresponding height to maintain the aspect ratio.
    const scaleFactor = desiredWidth / origWidth;
    const desiredHeight = origHeight * scaleFactor;

    // Place the image below the text.
    // Here we use a margin to separate the text and the image.
    const margin = 100;
    // The image's center Y position is calculated so that the image appears just below the text.
    const imageY = textY + margin + desiredHeight / 2;

    // Add the Bulifier icon image, set its display size, and center it horizontally.
    this.add.image(width / 2, imageY, 'bulifierIcon')
      .setOrigin(0.5)
      .setDisplaySize(desiredWidth, desiredHeight);

    // Transition to the GameScene after a delay.
    this.time.delayedCall(3000, () => {
      this.scene.start('GameScene');
    });
  }
}
