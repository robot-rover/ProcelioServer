## User-Server Endpoints

### Errors
These errors may occur at any point and should be handled appropriately.
- `401` -> You may be missing the correct Authentication.
- `500` -> There was a server side error, display a comforting error to the end user.

### Authentication
##### Bearer Token
The OAuth2 bearer token can be obtained by logging in with the Authenticate endpoint. It must be attached to every
authenticated request under the header `Authorization`, in the form `Bearer <token>`.

##### Server Bearer Token
The server has a static Token that is known to be owned by the server.

### Client Endpoints

##### Server Status `/status` - `GET`
- Gives the current status of the server.
- Response: Json (procul.studios.pojo.response.Message)

##### Create User `/users` - `POST`
- Creates a new user.
- Request: Json (procul.studios.pojo.request.Authenticate)
- Errors
  - `400` -> Missing Arguments
  - `409` -> Username already exists
- Response: Json (procul.studios.pojo.response.Message)

##### Authenticate `/login` - `POST`
- Logs in and obtains an OAuth2 access token.
- Request: Json (procul.studios.pojo.request.Authenticate)
- Errors
  - `400` -> Missing Arguments
  - `401` -> Username or Password incorrect
- Response: Json (procul.studios.pojo.response.Token)

##### Get My User `/users/me` - `GET`
- Gets information about your user.
- Errors
  - `404` -> UserId doesn't exist
- Response: Json (procul.studios.pojo.response.User)
  - field: (avatar) only present if exists
- Authorization: Bearer Token

##### Get a User `/users/{userId}` - `GET`
- Gets information about a user.
- Errors
  - `404` -> UserId doesn't exist
- Response: Json (procul.studios.pojo.response.User)
  - field: (avatar) only present if exists
- Authorization: Bearer Token

##### Get a User's Avatar `/users/{userId}/avatar` - `GET`
- Gets a user's avatar
- Errors
  - `404` -> The user doesn't have an avatar
- Response: Binary (PNG) 128x128
- Authentication: Bearer Token

##### Set my Avatar `/users/me/avatar` - `POST`
- Sets your avatar
- Request: Binary (Image)
- Errors
  - `400` -> The image is provided in an unrecognized format
  - `403` -> Tried to set an avatar that isn't yours
- Response: Json (procul.studios.pojo.response.User)
  - includes fields: (id, avatar)
- Authentication: Bearer Token

##### Purchase Blocks `/purchase` - `POST`
- Purchases some blocks
- Request: Binary (Inventory)
  - Map of BlockID, Quantity to Purchase
  - Negative Quantity sells
- Errors
  - `400` -> Inventory Body is invalid
  - `409` -> The user doesn't have enough currency or items
    - Can display message to user via (procul.studios.pojo.response.Message)
- Response: Json (procul.studios.pojo.response.User)
  - Includes fields: (currency, inventory)
- Authentication: Bearer Token

##### Create Robot `/users/me/robots` - `POST`
- Creates a new garage slot
- Request: Json (procul.studios.pojo.RobotInfo)
  - only allows field: (name)
- Errors
  - `400` -> Missing arguments
  - `403` -> Tried to create for a user that isn't you
- Response: Json (procul.studios.pojo.RobotInfo)
- Authentication: Bearer Token

##### Delete Robot `/users/me/robots/{garageSlot}` - `DEL`
- Deletes a Robot
- Errors
  - `400` -> {garageSlot} isn't a valid integer
  - `403` -> Tried to delete a robot that isn't yours
  - `404` -> Robot doesn't exist
- Response: Json (procul.studios.pojo.response.Message)
- Authentication: Bearer Token

##### Edit Robot `/users/me/robots/{garageSlot}` - `PATCH`
- Edits a Robot
- Request: Json (procul.studios.pojo.Robot)
- Errors
  - `400` -> {garageSlot} isn't a valid integer
  - `403` -> Tried to delete a robot that isn't yours
  - `404` -> Robot doesn't exist
- Response: Json (procul.studios.pojo.response.User)
- Authentication: Bearer Token

##### Edit Credentials `/users/me` - `PATCH`
- Edits your username and/or password
- Request: Json (procul.studios.pojo.request.Authenticate)
- Errors
  - `401` -> Old credentials are incorrect
  - `409` -> Username is already taken
- Response: Json (procul.studios.pojo.response.Message)
- Authentication: Bearer Token
  - Old username provided in header named X-Username
  - Old password provided in header named X-Username
- Old credentials provided in headers, new credentials in the request body. Either new credentials may be left null.

##### Get Robots `/users/me/robots` - `GET`
- Gets a list of robots for a user
- Errors
  - `400` -> Requested user id isn't valid
  - `404` -> Requested user doesn't exist
- Response: Json (procul.studios.pojo.RobotInfo) Array
- Authentication: Bearer Token

##### Get Robot `/users/me/robots` - `GET`
- Gets the binary representation of a robot
- Errors
  - `400` -> Requested garageSlot isn't valid
  - `404` -> Requested garageSlot doesn't exist
- Response: Binary (Robot)
- Authentication: Bearer Token

##### Get Inventory `/users/me/inventory` - `GET`
- Gets the users current inventory
- Response: Binary (Inventory)
- Authentication: Bearer Token

##### Get Statfile Hash `/statfile/hash` - `GET`
- Gets the MD5 hash of the current statfile
- Response: Json (procul.studios.pojo.response.Message)
- Authentication: Bearer Token

##### Get Statfile `/statfile` - `GET`
- Gets the binary representation of the current statfile
- Response: Binary (Stat File)
- Authentication: Bearer Token

### Server Endpoints

##### Validate User Token `/validate` - `GET`
- Ensures that a token is valid and matches the user.
- Request: Attach the user token in a header named `X-User-Token`
- Errors
  - `400` -> Missing Token Header
  - `404` -> User or Token does not exist and is not valid
- Response: Json (procul.studios.pojo.response.User)
  - Only the field: (id) is filled
- Authentication: Server Bearer Token

##### Rewards `/reward` - `POST`
- Gives users rewards at the end of a match
- Request: Json (procul.studios.pojo.response.User)
  - only fill fields: (id, currency, xp)
    - currency and xp are the amount to add to the users current amount
- Response: Json (procul.studios.pojo.response.Message)
- Authentication: Server Bearer Token