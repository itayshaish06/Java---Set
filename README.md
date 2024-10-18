# Java Concurrency and Synchronization Assignment

This project is an implementation of a simplified version of the card game **Set** in Java, focusing on concurrency and synchronization. The primary objective is to practice concurrent programming using Java Threads and Synchronization mechanisms.

## Table of Contents

- [Overview](#overview)
- [Game Design](#game-design)
- [Concurrency and Synchronization](#concurrency-and-synchronization)
- [Unit Testing](#unit-testing)
- [Build and Run Instructions](#build-and-run-instructions)
- [Directory Structure](#directory-structure)
- [Bonus Challenges](#bonus-challenges)

## Overview

The goal of this project is to implement the game logic for a simplified version of the card game "Set".

## Game Design

The game is played with a deck of 81 cards, each card having four features:
- **Color**: Red, Green, or Purple
- **Number**: 1, 2, or 3 shapes
- **Shape**: Squiggle, Diamond, or Oval
- **Shading**: Solid, Partial, or Empty

Players take turns identifying sets of cards where each feature is either all the same or all different. The dealer manages the game, shuffling cards, and checking if the sets identified by players are valid.

A “legal set” is defined as a set of 3 cards, that for each one of the four features — color, number, shape, and shading — the three cards must display that feature as either: 
  - (a) all the same, or: 
  - (b) all different
  - (in other words, for each feature the three cards must avoid having two cards showing one version of the feature and the remaining card showing a different version).
![image](https://github.com/user-attachments/assets/6a399567-d1da-46c3-85d8-696552e0f42e)

### Main Components

- **Dealer**: Manages the game flow, game timer, dealing cards, checking sets, and updating scores.
- **Players**: Represented as threads, each player interacts with the game by selecting cards. Both human and non-human players are supported.
- **Table**: A 3x4 grid where cards are placed for players to select. Players mark cards by placing tokens on them.

## Concurrency and Synchronization

Concurrency is at the heart of this project. Multiple player threads run simultaneously, interacting with the game table. Key synchronization points include:
- **Player Actions**: Each player operates independently, selecting cards and attempting to form sets. Synchronization ensures that only one set is evaluated at a time.
- **Dealer Operations**: The dealer thread manages the game state and must ensure fair handling of player requests in a first-come, first-served manner.

## Build and Run Instructions

This project uses [Maven](https://maven.apache.org/) for building and dependency management.

### Build and Run
To compile and run tests:
1. (Optional) Enter and edit the confiuration file `config.properties`.
2. Run the `buildSet.sh` script for linux or `buildSet.bat` script for windows and play.
