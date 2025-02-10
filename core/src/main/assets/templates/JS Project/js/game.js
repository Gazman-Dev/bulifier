class GameScene extends Phaser.Scene {
  constructor() {
    super({ key: 'GameScene' });
  }

  preload() {
    // No external assets are loaded since we’re using shapes.
  }

  create() {
    // Set a sky-blue background.
    this.cameras.main.setBackgroundColor('#87CEEB');

    // Create a simple circle texture for the bird if it doesn’t already exist.
    if (!this.textures.exists('birdTexture')) {
      const birdGraphics = this.make.graphics({ x: 0, y: 0, add: false });
      birdGraphics.fillStyle(0xffd700, 1); // Gold color.
      birdGraphics.fillCircle(15, 15, 15); // Circle of radius 15.
      birdGraphics.generateTexture('birdTexture', 30, 30);
    }

    // Create a rectangle texture for the pipes if it doesn’t exist.
    if (!this.textures.exists('pipeTexture')) {
      const pipeGraphics = this.make.graphics({ x: 0, y: 0, add: false });
      pipeGraphics.fillStyle(0x228B22, 1); // Forest green.
      pipeGraphics.fillRect(0, 0, 50, 300); // 50px x 300px rectangle.
      pipeGraphics.generateTexture('pipeTexture', 50, 300);
    }

    // Create the bird sprite using the generated texture.
    this.bird = this.physics.add.sprite(100, this.cameras.main.centerY, 'birdTexture');
    this.bird.setGravityY(800);
    this.bird.setCollideWorldBounds(true);

    // Input listeners for flapping.
    this.input.on('pointerdown', this.flap, this);
    this.input.keyboard.on('keydown-SPACE', this.flap, this);

    // Group for pipes.
    this.pipes = this.physics.add.group();

    // Timer to spawn pipes every 1500ms.
    this.pipeTimer = this.time.addEvent({
      delay: 1500,
      callback: this.spawnPipes,
      callbackScope: this,
      loop: true
    });

    // Score setup.
    this.score = 0;
    this.scoreText = this.add.text(16, 16, 'Score: 0', {
      fontSize: '32px',
      fill: '#000'
    }).setDepth(10);

    // Collision detection between the bird and pipes.
    this.physics.add.collider(this.bird, this.pipes, this.gameOver, null, this);
  }

  flap() {
    // Make the bird jump.
    this.bird.setVelocityY(-300);
  }

  spawnPipes() {
    const gapSize = 150; // Gap between pipes.
    // Determine a random vertical gap position.
    const gapPosition = Phaser.Math.Between(100, this.cameras.main.height - 100 - gapSize);

    // Create the top pipe (flipped vertically).
    const topPipe = this.pipes.create(this.cameras.main.width, gapPosition, 'pipeTexture');
    topPipe.setOrigin(0, 1);
    topPipe.body.allowGravity = false;
    topPipe.setVelocityX(-200);

    // Create the bottom pipe.
    const bottomPipe = this.pipes.create(this.cameras.main.width, gapPosition + gapSize, 'pipeTexture');
    bottomPipe.setOrigin(0, 0);
    bottomPipe.body.allowGravity = false;
    bottomPipe.setVelocityX(-200);

    // Mark pipes as not yet scored.
    topPipe.scored = false;
    bottomPipe.scored = false;
  }

  update() {
    // End the game if the bird leaves the screen.
    if (this.bird.y <= 0 || this.bird.y >= this.cameras.main.height) {
      this.gameOver();
    }

    // Increase score when pipes pass the bird.
    this.pipes.getChildren().forEach(pipe => {
      if (!pipe.scored && pipe.x + pipe.width < this.bird.x) {
        pipe.scored = true;
        this.score += 0.5; // Each pair counts as 1.
        if (Number.isInteger(this.score)) {
          this.scoreText.setText('Score: ' + this.score);
        }
      }
    });
  }

  gameOver() {
    // Stop the physics and pipe generation.
    this.physics.pause();
    this.pipeTimer.remove();
    this.bird.setTint(0xff0000);

    // Restart the scene after a short delay.
    this.time.addEvent({
      delay: 1000,
      callback: () => {
        this.scene.restart();
      },
      callbackScope: this
    });
  }
}
