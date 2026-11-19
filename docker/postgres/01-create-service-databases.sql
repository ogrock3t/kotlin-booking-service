\getenv room_user ROOM_DB_USER
\getenv room_password ROOM_DB_PASSWORD
\getenv booking_user BOOKING_DB_USER
\getenv booking_password BOOKING_DB_PASSWORD

CREATE ROLE :"room_user" LOGIN PASSWORD :'room_password';
CREATE DATABASE rooms OWNER :"room_user";
REVOKE CONNECT ON DATABASE rooms FROM PUBLIC;

CREATE ROLE :"booking_user" LOGIN PASSWORD :'booking_password';
CREATE DATABASE booking OWNER :"booking_user";
REVOKE CONNECT ON DATABASE booking FROM PUBLIC;
