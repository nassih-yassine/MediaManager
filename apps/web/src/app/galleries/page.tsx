import { redirect } from "next/navigation";

// Galleries live inside the Library's Pictures tab now.
export default function GalleriesPage() {
  redirect("/?kind=picture");
}
