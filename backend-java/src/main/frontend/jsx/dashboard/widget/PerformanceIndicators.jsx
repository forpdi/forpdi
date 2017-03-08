import React from "react";
import {Chart} from 'react-google-charts';


export default React.createClass({
  getInitialState() {
    return {
      hide:false
    };
  },
  componentDidMount() {
    var me = this;


      this.setState({
        options:{
          title: '',
          hAxis: {title: '', minValue: 0, maxValue: 15},
          vAxis: {title: 'Esperado x Alcançado', minValue: 0, maxValue: 15},
          legend: 'none',
          bar: {groupWidth: "75%"},
          isStacked: true
        },

        data: [ 
          ["Genero","Alcançado ", "Esperado"], // Esperado, Alcançado, "annotation"
          ["4 cursos em 2015",10, 15],    
          ["6 cursos em 2016",10,20], // Valor do Esperado sera (esperado - alcancado)           
          ["4 cursos em 2017",5,15]
        ]
        
      });

     
   
  },
  componentWillUnmount() {

  },

    hideFields() {
    this.setState({
      hide: !this.state.hide
    })
  },



  render() {
    return (
      <div className="panel panel-default">
        <div className="panel-heading">
          <b className="budget-graphic-title"> Desempenho dos indicadores </b>
          <div className="performance-strategic-btns floatRight">
            <span  className={(this.state.hide)?("mdi mdi-chevron-right marginLeft15"):("mdi mdi-chevron-down marginLeft15")}  onClick={this.hideFields}/>
          </div>
        </div>
         {!this.state.hide ?
          <div className="performance-strategic-graph">
             <Chart chartType="ColumnChart" data={this.state.data} options={this.state.options} graph_id="ColumnChart-PerformanceIndicators"  width={"100%"} height={"400px"} />
          </div>
        :""}
    </div>);
    }
});