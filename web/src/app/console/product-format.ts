// Nombre de cada tipo de producto, compartido por Productos y la bandeja de revisión (E-10).
import { ProductoTipoEnum } from '../api';

export const productNames: Record<ProductoTipoEnum, string> = {
  [ProductoTipoEnum.Microcredito]: 'Microcrédito',
  [ProductoTipoEnum.Verde]: 'Crédito ecológico',
  [ProductoTipoEnum.Pyme]: 'Crédito PYME',
};
