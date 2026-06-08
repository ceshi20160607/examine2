import { mountApp } from "./App";
import "./styles.css";

const container = document.querySelector<HTMLElement>("#app");

if (!container) {
  throw new Error("Missing #app container.");
}

mountApp(container);
