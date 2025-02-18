package com.bulifier.core.ai

import com.bulifier.core.ai.parsers.BulletFilesParser
import org.junit.Assert.*
import org.junit.Test

class ResponseProcessorTest {

    @Test
    fun testExample1() {
        val files = BulletFilesParser.parse(EXAMPLE1)
        val output = files.map {
            it.fullPath
        }.sorted().joinToString()

        val expected = listOf(
            "config.py.bul",
            "food.py.bul",
            "game.py.bul",
            "main.py.bul",
            "snake.py.bul"
        )

        assertEquals(expected.joinToString(), output)
    }

    @Test
    fun testExample2() {
        val files = BulletFilesParser.parse(EXAMPLE2)
        val output = files.map {
            it.fullPath
        }.sorted().joinToString()

        val expected = listOf(
            "index.html.bul",
            "js/main.js.bul"
        )

        assertEquals(expected.joinToString(), output)
    }

    @Test
    fun testExample3() {
        val files = BulletFilesParser.parse(EXAMPLE3)
        val output = files.map {
            it.fullPath
        }.sorted().joinToString()

        val expected = listOf(
            "css/styles.css.bul",
            "index.html.bul",
            "js/main.js.bul"
        )

        assertEquals(expected.joinToString(), output)
    }
}

private const val EXAMPLE1 = """
    FileName: config.py

- Purpose: Defines game configuration constants.
- Definitions:
  - Constants:
    - WIDTH = 600
    - HEIGHT = 400
    - BLOCK_SIZE = 10
    - SNAKE_SPEED = 15
    - WHITE, BLACK, RED, GREEN, BLUE (RGB color tuples)

FileName: snake.py

- Purpose: Defines the Snake class.
- Imports:
  - import config.py
- Class: Snake
  - Attributes:
    - size: config.BLOCK_SIZE
    - position: [config.WIDTH // 2, config.HEIGHT // 2]
    - body: List containing position
    - direction: 'RIGHT'
  - Methods:
    - change_direction(new_direction): Updates direction.
    - move(): Updates position based on direction.
    - grow(): Increases length of the snake.
    - trim_tail(): Maintains snake's length.
    - has_collision(width, height): Checks for collisions.

FileName: food.py

- Purpose: Defines the Food class.
- Imports:
  - import config.py
- Class: Food
  - Attributes:
    - width: config.WIDTH
    - height: config.HEIGHT
    - size: config.BLOCK_SIZE
    - position: Initialized via generate_position()
  - Methods:
    - generate_position(): Returns random position within bounds.
    - draw(surface, color): Draws food on the screen.

FileName: game.py

- Purpose: Orchestrates the main game logic.
- Imports:
  - import pygame
  - import snake.py
  - import food.py
  - import config.py
- Class: Game
  - Attributes:
    - screen: Pygame display surface.
    - clock: Controls game speed.
    - font: For rendering text.
    - snake: Instance of Snake.
    - food: Instance of Food.
    - score: Starts at 0.
    - running: Game state flag.
  - Methods:
    - display_score(): Shows current score.
    - handle_events(): Processes user input.
    - update_game_state(): Updates positions and checks collisions.
    - render(): Draws game elements.
    - run(): Main game loop.
    - show_game_over(): Displays game over screen.

FileName: main.py

- Purpose: Entry point to start the game.
- Imports:
  - import game.py
- Execution:
  - Instantiate Game.
  - Call game.run().
"""

const val EXAMPLE2 = """
---

**FileName:** index.html

- **Purpose:**  
  Entry point for the endless runner game, container for Three.js renderer and dependencies.

- **Structure:**
  - **HTML5 doctype with head:**
    - **title:** Endless Runner
    - **charset:** UTF-8
    - **meta viewport:** `width=device-width, initial-scale=1.0`
    - **Styles:** `css/styles.css`
  - **Body contains:**
    - **Container div:**  
      - `id="game-container"` for Three.js rendering.
    - **Preserved comment:**  
      - `<!-- Vendor libraries -->`
    - **Script tags:**
      - Three.js library via CDN:  
        - `https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js`
      - `js/main.js`

---

**FileName:** js/main.js

- **Purpose:**  
  Implements the endless runner game logic using Three.js, handling game initialization, mechanics, and rendering.

- **Imports:**
  - None (Three.js is loaded via script tag in `index.html`).

- **Definitions:**
  - **Attributes:**
    - `scene`: Initializes a Three.js Scene object.
    - `camera`: Sets up a PerspectiveCamera for the view.
    - `renderer`: Configures the WebGLRenderer for rendering the scene.
    - `player`: Instance of the Player class representing the runner.
    - `obstacles`: Array to manage active obstacles in the game.
    - `gameSpeed`: Number defining the speed at which obstacles move.
    - `isGameOver`: Boolean flag to track the game state.
    - `clock`: Three.js Clock to manage time-based animations.
  
  - **Classes:**
    - **Player:**
      - **Attributes:**
        - `mesh`: Three.js Mesh representing the player character.
        - `velocityY`: Current vertical velocity of the player.
      - **Methods:**
        - `jump()`:  
          - Applies an upward force to the player by setting `velocityY` to a negative value.
        - `update(deltaTime)`:  
          - Updates the player's position based on `velocityY` and applies gravity over the elapsed `deltaTime`.
    
    - **Obstacle:**
      - **Attributes:**
        - `mesh`: Three.js Mesh representing an obstacle.
      - **Methods:**
        - `update(deltaTime)`:  
          - Moves the obstacle leftwards based on `gameSpeed` and removes it from the scene if it goes out of bounds.

- **Methods:**
  - `init()`:
    - Initializes the Three.js `scene`, `camera`, and `renderer`.
    - Configures renderer to fit the window size and appends it to the `game-container`.
    - Creates and adds ambient and directional lights to the scene.
    - Instantiates the `player` object and adds it to the scene.
    - Sets up event listeners for user input (e.g., touch or click) to trigger the player's `jump()`.
    - Starts the game loop by calling `animate()`.
  
  - `animate()`:
    - Uses `requestAnimationFrame` to create a game loop.
    - Calculates `deltaTime` using `clock`.
    - Updates the `player` and all active `obstacles`.
    - Spawns new obstacles at regular intervals based on elapsed time.
    - Checks for collisions between the `player` and obstacles to set `isGameOver`.
    - Renders the updated scene.
    - If `isGameOver` is true, stops the game loop and displays a game over state.
  
  - `spawnObstacle()`:
    - Creates a new instance of the `Obstacle` class.
    - Positions the obstacle at a predetermined distance ahead of the player.
    - Adds the obstacle to the `scene` and `obstacles` array.
  
  - `handleInput()`:
    - Listens for user interactions such as clicks or touch events.
    - Calls the `player.jump()` method in response to input to make the player character jump.

---
"""

const val EXAMPLE3 = """index.html

- Purpose: Entry point for the endless runner game, sets up the HTML structure and includes necessary scripts.
- Structure:
  - HTML5 doctype with head:
    - title: Runner5
    - charset: UTF-8
    - viewport: width=device-width, initial-scale=1.0
    - styles: css/styles.css
  - Body contains:
    - canvas element with id `gameCanvas` for Three.js rendering
    - Preserved <!-- Vendor libraries --> comment
    - Script tags:
      - type="module" src="js/main.js"

css/styles.css

- Purpose: Styling for the game canvas and overall layout to ensure mobile friendliness.
- Imports: None
- Definitions:
  - CSS Rules:
    - body:
      - margin: 0
      - overflow: hidden
      - background-color: #000000
    - canvas#gameCanvas:
      - display: block
      - width: 100vw
      - height: 100vh

js/main.js

- Purpose: Initializes the Three.js scene and starts the game loop.
- Imports:
  - js/game.js
- Definitions:
  - Attributes:
    - scene: Three.js Scene object.
    - camera: Three.js Camera object.
    - renderer: Three.js Renderer object.
    - game: Instance of Game class.
  - Functions:
    - initialize():
      - Sets up the Three.js scene, camera, and renderer.
      - Appends renderer.domElement to the canvas in index.html.
      - Creates an instance of Game and starts the game loop.
    - animate():
      - Calls requestAnimationFrame to create a loop.
      - Updates the game state and renders the scene each frame.
- Methods:
  - initialize():
    - Sets up the Three.js environment and starts the animation loop.
  - animate():
    - Continuously updates and renders the game scene."""