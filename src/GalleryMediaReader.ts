import Detonator from "detonator";

export interface GalleryMedia {
  id: string;
  type: "image" | "video";
  source: string;
  thumbnail: string;
  width: number;
  height: number;
  duration: number | null;
  rotation: number | null;
}

export class GalleryMediaReader {
  private static ID: number = 0;

  private id: number = GalleryMediaReader.ID++;

  private offset: number = 0;

  private albumId: string | null;
  private limit: number;

  constructor({
    limit,
    albumId = null,
  }: {
    limit: number;
    albumId?: string | null;
  }) {
    this.limit = limit;
    this.albumId = albumId;
  }

  public async read(): Promise<GalleryMedia[]> {
    const mediaList: GalleryMedia[] = await Detonator.request(
      "com.iconshot.detonator.gallery.mediareader::read",
      {
        id: this.id,
        limit: this.limit,
        offset: this.offset,
        albumId: this.albumId,
      }
    ).fetchAndDecode();

    this.offset += mediaList.length;

    return mediaList;
  }

  public async close(): Promise<void> {
    this.offset = 0;

    await Detonator.request(
      "com.iconshot.detonator.gallery.mediareader::close",
      { id: this.id }
    ).fetch();
  }
}
