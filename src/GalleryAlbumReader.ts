import Detonator from "detonator";

export interface GalleryAlbum {
  id: string;
  name: string;
  mediaCount: number;
  thumbnail: string | null;
}

export class GalleryAlbumReader {
  private static ID: number = 0;

  private id: number = GalleryAlbumReader.ID++;

  private offset: number = 0;

  private limit: number;

  constructor({ limit }: { limit: number }) {
    this.limit = limit;
  }

  public async read(): Promise<GalleryAlbum[]> {
    const albumList: GalleryAlbum[] = await Detonator.request(
      "com.iconshot.detonator.gallery.albumreader::read",
      { id: this.id, limit: this.limit, offset: this.offset }
    ).fetchAndDecode();

    this.offset += albumList.length;

    return albumList;
  }

  public async close(): Promise<void> {
    this.offset = 0;

    await Detonator.request(
      "com.iconshot.detonator.gallery.albumreader::close",
      { id: this.id }
    ).fetch();
  }
}
