DROP TABLE IF EXISTS reviews, payments, bookings, availability_blocks, listing_photos, listings, users;
--;;
DROP FUNCTION IF EXISTS checar_reserva_vs_bloqueios();
--;;
DROP FUNCTION IF EXISTS checar_bloqueio_vs_reservas();
