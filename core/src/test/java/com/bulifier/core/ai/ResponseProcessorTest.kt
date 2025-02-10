package com.bulifier.core.ai

import org.junit.Assert.*
import org.junit.Test

class ResponseProcessorTest {

    @Test
    fun parseResponse() {
        val basePath = "com/games/snake"
        val files = ResponseProcessor().parseResponse(EXAMPLE1)
        verifyFile(files, basePath, "config.py", 0)
        verifyFile(files, basePath, "snake.py", 1)
        verifyFile(files, basePath, "food.py", 1)
        verifyFile(files, basePath, "game.py", 4)
        verifyFile(files, basePath, "main.py", 1)
    }

    private fun verifyFile(
        files: List<FileData>,
        basePath: String,
        fileName: String,
        importsCount: Int
    ) {
        val file = files.firstOrNull { it.fullPath == "$basePath/$fileName" }
        assertNotNull("failed $fileName", file)
        assertTrue("failed $fileName", file?.imports?.size == importsCount)
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