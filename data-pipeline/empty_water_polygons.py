# Writes a valid, EMPTY polygon shapefile zip as a stand-in for OSM water polygons
# (used only because osmdata.openstreetmap.de was unreachable). Ocean areas will be missing.
import struct, zipfile, sys, io
def shp_header(length_words):
    h = struct.pack('>i', 9994) + b'\0'*20 + struct.pack('>i', length_words)
    h += struct.pack('<ii', 1000, 5) + struct.pack('<8d', 0,0,0,0,0,0,0,0)
    return h
shp = shp_header(50); shx = shp_header(50)
# dBASE III with one numeric field "FID", zero records
fields = b'FID'.ljust(11, b'\0') + b'N' + b'\0'*4 + bytes([10, 0]) + b'\0'*14
hdr_len = 32 + len(fields) + 1
dbf = struct.pack('<BBBBIHH20x', 3, 126, 11, 9, 0, hdr_len, 11) + fields + b'\r' + b'\x1a'
prj = 'PROJCS["WGS 84 / Pseudo-Mercator",GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]],PROJECTION["Mercator_1SP"],PARAMETER["central_meridian",0],PARAMETER["scale_factor",1],PARAMETER["false_easting",0],PARAMETER["false_northing",0],UNIT["metre",1]]'
with zipfile.ZipFile(sys.argv[1], 'w') as z:
    for ext, data in (('shp', shp), ('shx', shx), ('dbf', dbf), ('prj', prj.encode())):
        z.writestr(f'water-polygons-split-3857/water_polygons.{ext}', data)
