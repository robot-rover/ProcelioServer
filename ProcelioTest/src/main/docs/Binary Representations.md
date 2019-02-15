# Binary Representations

## Garages

| bytes | description |
|-------|-------------|
| 4 | Number of Robots |
| n | Array of Garage Slots |

##### Garage Slot

| bytes | description |
|-------|-------------|
| 4 | Garage Slot Number |
| n | Robot |

## Robot

| bytes | description |
|-------|-------------|
| 4 | Magic Number: 0xC571B040 |
| 4 | Save Format Version |
| 8 | Blank (Reserved for Metadata) |
| 1 | Name String Length |
| n | Name String (UTF-8) |
| 4 | Number of Parts |
| n |  Array of Parts |
| 16| 128-bit MD5 Checksum of Rest |

##### Part

| bytes | description |
|-------|-------------|
| 1 | X Position |
| 1 | Y Position |
| 1 | Z Position |
| 1 | Rotation |
| 1 | Red Color Component |
| 1 | Blue Color Component |
| 1 | Green Color Component |
| 2 | Part ID |

## Inventory

| bytes | description |
|-------|-------------|
| 4 | Magic Number: 0x10E10CBA |
| 4 | Version |
| 4 | Number of Entries |
| n | Array of Entries |

##### Entry

| bytes | description |
|-------|-------------|
| 2 | Part ID |
| 4 | Part Quantity |

## Stat File

| bytes | description |
|-------|-------------|
| 4 | Magic Number: 0x1EF1A757 |
| 4 | Number of Blocks |
| n | Array of Blocks |

##### Block

| bytes | description |
|-------|-------------|
| 2 | Part ID |
| 1 | Number of Flags |
| n | Array of Flags |

##### Flag

| bytes | description |
|-------|-------------|
| 1 | Flag Type |
| 4 | Flag Value |