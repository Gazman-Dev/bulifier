package com.bulifier.core.ai

import com.bulifier.core.ai.parsers.RawFilesParser
import org.junit.Assert.assertEquals
import org.junit.Test

class RawFilesParserTest {

    @Test
    fun testResponse1() {
        val files = RawFilesParser.parse(response1)
        assertEquals(
            files.map {
                it.fullPath
            }, listOf(
                "css/styles.css",
                "index.html",
                "js/game.js",
                "js/main.js",
                "js/splash.js"
            )
        )
    }

    @Test
    fun testResponse2() {
        val files = RawFilesParser.parse(response1)
        assertEquals(
            files.map {
                it.fullPath
            }, listOf(
                "css/styles.css",
                "index.html",
                "js/game.js",
                "js/main.js",
                "js/splash.js"
            )
        )
    }
}

val response1 = """
    Given the extensive list of files you have asked for, let's focus on the primary ones related to your game's structure for now. If any non-essential files require attention outside of what I've focused on, please share more details.

    ### Updated Native Code:

    #### css/styles.css
    ```css
    /* Import your special fonts */
    @font-face {
      font-family: 'SpecialFont';
      src: url('../assets/fonts/SuperShiny.ttf') format('truetype');
    }

    body {
      margin: 0;
      padding: 0;
      background-color: #000; /* Updated to black */
      color: #fff;
      font-family: 'SpecialFont', sans-serif;
    }

    #game-container {
      width: 100vw;
      height: 100vh;
      overflow: hidden;
    }

    /* Styling for splash screen text */
    .splash-text {
      font-size: 48px;
      text-align: center;
      margin-top: 20%;
    }

    .splash-icon {
      display: block;
      margin: 20px auto;
      width: 150px;
      height: auto;
    }
    ```

    #### index.html
    ```html
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <title>Birdy</title>
        <link rel="stylesheet" href="css/styles.css">
    </head>
    <body>
    <!-- Phaser will render the game here -->
    <div id="game-container"></div>

    <!-- Vendor libraries -->

    <!-- Game code -->
    <script src="js/main.js"></script>
    <script src="js/splash.js"></script>
    <script src="js/game.js"></script>
    </body>
    </html>
    ```

    #### js/game.js
    ```javascript
    class GameScene extends Phaser.Scene {
      constructor() {
        super({ key: 'GameScene' });
      }

      preload() {
        // No external assets are loaded since we’re using shapes.
      }

      create() {
        this.cameras.main.setBackgroundColor('#87CEEB');

        if (!this.textures.exists('birdTexture')) {
          const birdGraphics = this.make.graphics({ x: 0, y: 0, add: false });
          birdGraphics.fillStyle(0xffd700, 1);
          birdGraphics.fillCircle(15, 15, 15);
          birdGraphics.generateTexture('birdTexture', 30, 30);
        }

        if (!this.textures.exists('pipeTexture')) {
          const pipeGraphics = this.make.graphics({ x: 0, y: 0, add: false });
          pipeGraphics.fillStyle(0x228B22, 1);
          pipeGraphics.fillRect(0, 0, 50, 300);
          pipeGraphics.generateTexture('pipeTexture', 50, 300);
        }

        this.bird = this.physics.add.sprite(100, this.cameras.main.centerY, 'birdTexture');
        this.bird.setGravityY(800);
        this.bird.setCollideWorldBounds(true);

        this.input.on('pointerdown', this.flap, this);
        this.input.keyboard.on('keydown-SPACE', this.flap, this);

        this.pipes = this.physics.add.group();

        this.pipeTimer = this.time.addEvent({
          delay: 1500,
          callback: this.spawnPipes,
          callbackScope: this,
          loop: true
        });

        this.score = 0;
        this.scoreText = this.add.text(16, 16, 'Score: 0', {
          fontSize: '32px',
          fill: '#000'
        }).setDepth(10);

        this.physics.add.collider(this.bird, this.pipes, this.gameOver, null, this);
      }

      flap() {
        this.bird.setVelocityY(-300);
      }

      spawnPipes() {
        const gapSize = 150;
        const gapPosition = Phaser.Math.Between(100, this.cameras.main.height - 100 - gapSize);

        const topPipe = this.pipes.create(this.cameras.main.width, gapPosition, 'pipeTexture');
        topPipe.setOrigin(0, 1);
        topPipe.body.allowGravity = false;
        topPipe.setVelocityX(-200);

        const bottomPipe = this.pipes.create(this.cameras.main.width, gapPosition + gapSize, 'pipeTexture');
        bottomPipe.setOrigin(0, 0);
        bottomPipe.body.allowGravity = false;
        bottomPipe.setVelocityX(-200);

        topPipe.scored = false;
        bottomPipe.scored = false;
      }

      update() {
        if (this.bird.y <= 0 || this.bird.y >= this.cameras.main.height) {
          this.gameOver();
        }

        this.pipes.getChildren().forEach(pipe => {
          if (!pipe.scored && pipe.x + pipe.width < this.bird.x) {
            pipe.scored = true;
            this.score += 0.5;
            if (Number.isInteger(this.score)) {
              this.scoreText.setText('Score: ' + this.score);
            }
          }
        });
      }

      gameOver() {
        this.physics.pause();
        this.pipeTimer.remove();
        this.bird.setTint(0xff0000);

        this.time.addEvent({
          delay: 1000,
          callback: () => {
            this.scene.restart();
          },
          callbackScope: this
        });
      }
    }
    ```

    #### js/main.js
    ```javascript
    window.onload = function() {
      const config = {
        type: Phaser.AUTO,
        parent: 'game-container',
        width: window.innerWidth,
        height: window.innerHeight,
        physics: {
          default: 'arcade',
          arcade: {
            debug: false
          }
        },
        scale: {
          mode: Phaser.Scale.FIT,
          autoCenter: Phaser.Scale.CENTER_BOTH
        },
        scene: [SplashScene, GameScene]
      };

      const game = new Phaser.Game(config);
    };
    ```

    #### js/splash.js
    ```javascript
    class SplashScene extends Phaser.Scene {
      constructor() {
        super({ key: 'SplashScene' });
      }

      preload() {
        this.load.image('bulifierIcon', 'assets/images/bulifier-logo.png');
      }

      create() {
        const { width, height } = this.cameras.main;

        const textY = height / 6;
        this.add.text(width / 2, textY, 'Built with Bulifier', {
          fontFamily: 'SpecialFont',
          fontSize: '48px',
          color: '#ffffff'
        }).setOrigin(0.5);

        const desiredWidth = width * 0.8;
        const textureSource = this.textures.get('bulifierIcon').getSourceImage();
        const origWidth = textureSource.width;
        const origHeight = textureSource.height;

        const scaleFactor = desiredWidth / origWidth;
        const desiredHeight = origHeight * scaleFactor;

        const margin = 100;
        const imageY = textY + margin + desiredHeight / 2;

        this.add.image(width / 2, imageY, 'bulifierIcon')
          .setOrigin(0.5)
          .setDisplaySize(desiredWidth, desiredHeight);

        this.time.delayedCall(3000, () => {
          this.scene.start('GameScene');
        });
      }
    }
    ```

    For additional native code files specified, such as `.bul` schemas or additional JavaScript files, please provide specific pseudo code as these typically would be domain-specific or context-specific files. If any additional files or details are essential, let me know!
""".trimIndent()

val response2 = """
    Certainly! I'll convert the provided pseudo code files into native code files for you. Here are the full native code files for each of the specified pseudo code files:

    ### Native `css/styles.css`
    ```css
    /* Import your special fonts */
    @font-face {
      font-family: 'SpecialFont';
      src: url('../assets/fonts/SuperShiny.ttf') format('truetype');
    }

    body {
      margin: 0;
      padding: 0;
      background-color: #000;
      color: #fff;
      font-family: 'SpecialFont', sans-serif;
    }

    #game-container {
      width: 100vw;
      height: 100vh;
      overflow: hidden;
    }

    /* Styling for splash screen text */
    .splash-text {
      font-size: 48px;
      text-align: center;
      margin-top: 20%;
    }

    .splash-icon {
      display: block;
      margin: 20px auto;
      width: 150px;
      height: auto;
    }
    ```

    ### Native `css/styles.css.bul`
    This file needs to be inferred as the pseudo code wasn't provided. However, it might be a specialized or minified css for the game, typically a `.bul` file could mean different variants, depending on the context or transformations, such as minified or environment-specific CSS but for now I would suggest following similar stylings.

    ```css
    body {
      margin: 0;
      padding: 0;
      background-color: #000;
      color: #fff;
      font-family: 'SpecialFont', sans-serif;
    }

    #game-container {
      width: 100vw;
      height: 100vh;
    }

    .splash-text {
      font-size: 48px;
      text-align: center;
      margin-top: 20%;
    }

    .splash-icon {
      display: block;
      margin: 20px auto;
      width: 150px;
    }
    ```

    ### Native `dependencies.txt`
    Since no direct pseudo code was provided, a typical `dependencies.txt` file might include listing dependencies but will assume a simple template for the game context.
    ```txt
    phaser@latest
    ```

    ### Native `/index.html`
    ```html
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <title>Birdy</title>
        <link rel="stylesheet" href="css/styles.css">
    </head>
    <body>
    <!-- Phaser will render the game here -->
    <div id="game-container"></div>

    <!-- Vendor libraries -->

    <!-- Game code -->
    <script src="js/main.js"></script>
    <script src="js/splash.js"></script>
    <script src="js/game.js"></script>
    </body>
    </html>
    ```

    ### Native `index.html.bul`
    Assuming this is a variation constrained by an unknown `.bul` standard or requirement, assume a similar need primarily altering tags.

    ```html
    <!DOCTYPE html>
    <html lang="en-bul">
    <head>
        <meta charset="UTF-8">
        <title>Birdy Bulified</title>
        <link rel="stylesheet" href="css/styles.css.bul">
    </head>
    <body>
    <div id="game-container"></div>
    <script src="js/main.js.bul"></script>
    <script src="js/splash.js.bul"></script>
    <script src="js/game.js.bul"></script>
    </body>
    </html>
    ```

    ### Native `js/game.js`
    ```javascript
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
    ```

    ### Native `js/game.js.bul`
    Here we treat it similarly as a variation constrained by an unknown `.bul` standard.
    ```javascript
    class GameScene extends Phaser.Scene {
      constructor() {
        super({ key: 'GameSceneBul' });
      }

      preload() {
        // Load specific assets for bulified variant.
      }

      create() {
        this.cameras.main.setBackgroundColor('#CCCCFF');

        if (!this.textures.exists('birdTextureBulified')) {
          // Modified graphics assuming changes in the bulified version
          const birdGraphics = this.make.graphics({ x: 0, y: 0, add: false });
          birdGraphics.fillStyle(0xFFD700, 1); // Gold color.
          birdGraphics.fillCircle(15, 15, 15);
          birdGraphics.generateTexture('birdTextureBulified', 30, 30);
        }

        // Assume bulified textures or configurations...

        this.bird = this.physics.add.sprite(100, this.cameras.main.centerY, 'birdTextureBulified');
        this.bird.setGravityY(800);
        this.bird.setCollideWorldBounds(true);

        this.input.on('pointerdown', this.flap, this);
        this.input.keyboard.on('keydown-SPACE', this.flap, this);

        this.pipes = this.physics.add.group();

        this.pipeTimer = this.time.addEvent({
          delay: 1500,
          callback: this.spawnPipes,
          callbackScope: this,
          loop: true
        });

        this.score = 0;
        this.scoreText = this.add.text(16, 16, 'Score: 0', {
          fontSize: '32px',
          fill: '#000'
        }).setDepth(10);

        this.physics.add.collider(this.bird, this.pipes, this.gameOver, null, this);
      }

      flap() {
        this.bird.setVelocityY(-300);
      }

      spawnPipes() {
        const gapSize = 150;
        const gapPosition = Phaser.Math.Between(100, this.cameras.main.height - 100 - gapSize);

        const topPipe = this.pipes.create(this.cameras.main.width, gapPosition, 'pipeTexture');
        topPipe.setOrigin(0, 1);
        topPipe.body.allowGravity = false;
        topPipe.setVelocityX(-200);

        const bottomPipe = this.pipes.create(this.cameras.main.width, gapPosition + gapSize, 'pipeTexture');
        bottomPipe.setOrigin(0, 0);
        bottomPipe.body.allowGravity = false;
        bottomPipe.setVelocityX(-200);

        topPipe.scored = false;
        bottomPipe.scored = false;
      }

      update() {
        if (this.bird.y <= 0 || this.bird.y >= this.cameras.main.height) {
          this.gameOver();
        }

        this.pipes.getChildren().forEach(pipe => {
          if (!pipe.scored && pipe.x + pipe.width < this.bird.x) {
            pipe.scored = true;
            this.score += 0.5;
            if (Number.isInteger(this.score)) {
              this.scoreText.setText('Score: ' + this.score);
            }
          }
        });
      }

      gameOver() {
        this.physics.pause();
        this.pipeTimer.remove();
        this.bird.setTint(0xff0000);

        this.time.addEvent({
          delay: 1000,
          callback: () => {
            this.scene.restart();
          },
          callbackScope: this
        });
      }
    }
    ```

    ### Native `js/main.js`
    ```javascript
    window.onload = function() {
      const config = {
        type: Phaser.AUTO,
        parent: 'game-container',
        width: window.innerWidth,
        height: window.innerHeight,
        physics: {
          default: 'arcade',
          arcade: {
            debug: false
          }
        },
        scale: {
          mode: Phaser.Scale.FIT,
          autoCenter: Phaser.Scale.CENTER_BOTH
        },
        scene: [SplashScene, GameScene]
      };

      const game = new Phaser.Game(config);
    };
    ```

    ### Native `js/main.js.bul`
    Again, inferred as a version can be adjusted according to any bulified guideline.
    ```javascript
    window.onload = function() {
      const config = {
        type: Phaser.AUTO,
        parent: 'game-container',
        width: window.innerWidth,
        height: window.innerHeight,
        physics: {
          default: 'arcade',
          arcade: {
            debug: false
          }
        },
        scale: {
          mode: Phaser.Scale.FIT,
          autoCenter: Phaser.Scale.CENTER_BOTH
        },
        scene: [SplashSceneBulified, GameSceneBulified] // Assume modified scenes.
      };

      const game = new Phaser.Game(config);
    };
    ```

    ### Native `js/splash.js`
    ```javascript
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
    ```

    ### Native `js/splash.js.bul`
    Here's a potential version adjustment based on assumptions.
    ```javascript
    class SplashSceneBulified extends Phaser.Scene {
      constructor() {
        super({ key: 'SplashSceneBulified' });
      }

      preload() {
        this.load.image('bulifierIcon', 'assets/images/bulifier-logo.png');
      }

      create() {
        const { width, height } = this.cameras.main;

        const textY = height / 6;
        this.add.text(width / 2, textY, 'Bulified Built', {
          fontFamily: 'SpecialFont',
          fontSize: '48px',
          color: '#ffffff'
        }).setOrigin(0.5);

        const desiredWidth = width * 0.8;
        const textureSource = this.textures.get('bulifierIcon').getSourceImage();
        const origWidth = textureSource.width;
        const origHeight = textureSource.height;

        const scaleFactor = desiredWidth / origWidth;
        const desiredHeight = origHeight * scaleFactor;

        const margin = 100;
        const imageY = textY + margin + desiredHeight / 2;

        this.add.image(width / 2, imageY, 'bulifierIcon')
          .setOrigin(0.5)
          .setDisplaySize(desiredWidth, desiredHeight);

        this.time.delayedCall(3000, () => {
          this.scene.start('GameSceneBulified');
        });
      }
    }
    ```

    ### `schemas/*.schema` Files
    The files requested under the schemas directory were not provided with pseudo code, thus for filling these completely without proper mapping analogies or content guidelines is speculative. schema files generally dictate structure and possible validations, not typically game code or user interface content. In such cases, it would require more specific requirements to correctly and meaningfully populate these. Here is a placeholder example for an empty schema:

    ```json
    {
      "${'$'}schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "properties": {}
    }
    ```

    These placeholders offer a basic outline to be filled in with concrete details based on project specifics but note that so much of schemas usually are built per standards such as JSON schema or OpenAPI for specific API definitions.

    ### js/path.js.bul, js/tower.js.bul, js/enemy.js.bul, js/toolbar.js.bul

    Without specific content, these files might relate to parts of the Tower Defense game mechanics potentially involving paths, tower placements/loading, enemies behavior, and gameplay toolbars. Normally these might include algorithms and classes with methods to handle positioning, interactions, spawning logic, rendering, and more. Here is a canvas for those with hypothetical comments:

    ```javascript
    // js/path.js.bul
    // Placeholder to define paths for towers or enemies

    class Path {
        constructor() {
            // Initialize path
        }
        
        generate() {
            // Logic to generate path points/nodes
        }
        
        draw() {
            // Visualize path
        }
    }
    ```

    ```javascript
    // js/tower.js.bul
    // Placeholder to manage towers

    class Tower {
        constructor() {
            // Initialize tower properties
        }
        
        place() {
            // Logic to place tower
        }
        
        shoot(target) {
            // Logic for tower shooting enemies
        }
    }
    ```

    ```javascript
    // js/enemy.js.bul
    // Placeholder for enemies

    class Enemy {
        constructor() {
            // Initialize enemy properties
        }
        
        move() {
            // Logic to move enemy along path
        }
        
        takeDamage(amount) {
            // Logic for enemy taking damage
        }
    }
    ```

    ```javascript
    // js/toolbar.js.bul
    // Placeholder for game toolbar interface

    class Toolbar {
        constructor() {
            // Initialize toolbar items
        }
        
        render() {
            // Logic to display toolbar
        }
        
        addButton(label, callback) {
            // Add button with action to toolbar
        }
    }
    ```

    This diverse collection of baseline examples across pseudo to native file types, highlight the context-driven necessity for object-specific code. Always tailor specifics to your project requirements usually rooted in technical design specifications which supplement the translations and improvements over the native outputs from pseudo code sketches.
""".trimIndent()