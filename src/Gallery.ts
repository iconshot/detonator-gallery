import Detonator from "detonator";

export class Gallery {
  static async requestPermission(): Promise<boolean> {
    return await Detonator.request(
      "com.iconshot.detonator.gallery::requestPermission"
    ).fetchAndDecode();
  }
}
