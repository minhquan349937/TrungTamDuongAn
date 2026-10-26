import { App } from "antd";
/** Context-backed modal APIs work with React 19 and inherit Vietnamese locale. */
export const useConfirm = () => App.useApp().modal;
